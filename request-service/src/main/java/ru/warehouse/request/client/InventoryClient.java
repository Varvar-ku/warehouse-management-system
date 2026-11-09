// InventoryClient.java
package ru.warehouse.request.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.warehouse.common.client.Remote;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.client.InventoryContract.*;
import java.util.List;
import java.util.UUID;
import ru.warehouse.common.dto.PageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import ru.warehouse.common.security.ServiceTokenIssuer;

/**
 * Вызовы inventory-service. Чтение (availability, резервы, документы) идёт с JWT пользователя (роль STOREKEEPER).
 * Изменяющие операции (резерв, снятие резерва, документы выдачи) идут со служебным токеном от имени request-service
 * и пользователя-исполнителя (P1-10): inventory принимает их только от request-service.
 */
@Component
public class InventoryClient {
    private static final String SERVICE = "inventory-service";
    private static final String CALLER = "request-service";
    private final RestClient client;
    private final RestClient serviceClient;
    private final ServiceTokenIssuer tokens;

    public InventoryClient(@Qualifier("inventoryRestClient") RestClient client,
                           @Qualifier("inventoryServiceRestClient") RestClient serviceClient,
                           ServiceTokenIssuer tokens) {
        this.client = client;
        this.serviceClient = serviceClient;
        this.tokens = tokens;
    }

    /** Служебный токен от имени пользователя текущего запроса (его идентификатор попадает в журналы inventory). */
    private String serviceAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("Внутренний вызов inventory без аутентифицированного пользователя");
        }
        return "Bearer " + tokens.issue(CALLER, UUID.fromString(jwt.getToken().getSubject()));
    }

    public List<AvailabilityItem> availability(UUID warehouseId, List<AvailabilityLine> lines) {
        List<AvailabilityItem> result = Remote.call(SERVICE, () -> client.post()
                .uri("/api/v1/reservations/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AvailabilityRequest(warehouseId, lines))
                .retrieve()
                .body(new ParameterizedTypeReference<List<AvailabilityItem>>() {}));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    /** Idempotency-Key обязателен: повтор после сбоя вернёт сохранённый ответ, а не создаст второй резерв. */
    public ReserveResponse reserve(ReserveRequest request, String idempotencyKey) {
        ReserveResponse result = Remote.call(SERVICE, () -> serviceClient.post()
                .uri("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ReserveResponse.class));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    /** Снятие резерва идемпотентно по природе: повторный вызов находит 0 активных резервов. */
    public ReleaseResponse release(UUID requestId) {
        return release(requestId, null);
    }

    /** B1: shortages (недобор по резервам) регистрируются в inventory как недостача с источником CANCELLATION. */
    public ReleaseResponse release(UUID requestId, List<ShortageMark> shortages) {
        List<ShortageMark> marks = shortages == null || shortages.isEmpty() ? null : shortages;
        ReleaseResponse result = Remote.call(SERVICE, () -> serviceClient.post()
                .uri("/api/v1/reservations/release")
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReleaseRequest(requestId, null, marks))
                .retrieve()
                .body(ReleaseResponse.class));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    public List<ReservationDto> reservations(UUID requestId) {
        List<ReservationDto> result = Remote.call(SERVICE, () -> client.get()
                .uri("/api/v1/reservations?requestId={id}", requestId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ReservationDto>>() {}));
        return result == null ? List.of() : result;
    }

    public IssueDto createIssue(CreateIssueRequest request) {
        IssueDto result = Remote.call(SERVICE, () -> serviceClient.post()
                .uri("/api/v1/issues")
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(IssueDto.class));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    /** Ключ зависит от документа: повторное подтверждение вернёт сохранённый ответ, а не вторую выдачу. */
    public IssueDto confirmIssue(UUID issueId) {
        IssueDto result = Remote.call(SERVICE, () -> serviceClient.post()
                .uri("/api/v1/issues/{id}/confirm", issueId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .header("Idempotency-Key", "iss-" + issueId)
                .retrieve()
                .body(IssueDto.class));
        if (result == null) throw ApiException.upstream("inventory-service вернул пустой ответ");
        return result;
    }

    /** S5: отмена черновика документа передачи (подтверждённый не отменяется, inventory вернёт 409). */
    public void cancelIssue(UUID issueId, String reason) {
        Remote.call(SERVICE, () -> serviceClient.post()
                .uri("/api/v1/issues/{id}/cancel", issueId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancelIssueRequest(reason))
                .retrieve()
                .toBodilessEntity());
    }

    /** Документы передачи по заявке (все статусы): нужны для восстановления после сбоя. */
    public List<IssueDto> issues(UUID requestId) {
        PageResponse<IssueDto> page = Remote.call(SERVICE, () -> client.get()
                .uri("/api/v1/issues?requestId={id}&size=100", requestId)
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<IssueDto>>() {}));
        return page == null || page.content() == null ? List.of() : page.content();
    }
}