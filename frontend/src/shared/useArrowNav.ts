import { useEffect } from 'react';
import { pickNext, type Dir } from './arrowGeometry';

const CANDIDATES = 'button:not([disabled]), a[href], [role="button"]:not([aria-disabled="true"])';

/** Элементы, у которых стрелки означают своё (списки, вкладки, меню, поля ввода). Их не перехватываем. */
const OWN_ARROWS =
  'input, textarea, select, [contenteditable="true"], [role="combobox"], [role="spinbutton"], [role="slider"], [role="switch"], ' +
  '[role="tab"], [role="menuitem"], [role="option"], [role="radio"], .ant-menu, .ant-dropdown, .ant-select-dropdown, ' +
  '.ant-picker-dropdown, .ant-tabs-nav, .ant-pagination-options';

const KEYS: Record<string, Dir> = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };

const visible = (el: HTMLElement) => el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';

/** Область, в которой ищем соседей: открытое окно или панель, иначе вся страница. */
function scopeOf(active: HTMLElement): ParentNode {
  return active.closest('.ant-modal, .ant-drawer, .ant-popover') ?? document;
}

function onKeyDown(e: KeyboardEvent) {
  const dir = KEYS[e.key];
  if (!dir || e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
  const active = document.activeElement;
  if (!(active instanceof HTMLElement) || !active.matches(CANDIDATES) || active.closest(OWN_ARROWS)) return;

  const scope = scopeOf(active);
  const inOverlay = scope !== document;
  const list = Array.from(scope.querySelectorAll<HTMLElement>(CANDIDATES)).filter(
    (el) => el !== active && visible(el) && !el.closest(OWN_ARROWS) && (inOverlay || !el.closest('.ant-modal, .ant-drawer, .ant-popover')),
  );
  if (list.length === 0) return;

  const idx = pickNext(active.getBoundingClientRect(), list.map((el) => el.getBoundingClientRect()), dir);
  if (idx < 0) return;
  e.preventDefault(); // иначе страница прокрутится
  list[idx].focus();
  list[idx].scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

/**
 * Навигация по кнопкам и ссылкам стрелками: ←→↑↓ переводят фокус на соседнюю кнопку по расположению на экране.
 * Enter и пробел нажимают кнопку (это делает браузер). В полях ввода, списках, вкладках и меню стрелки остаются своими.
 * Подключается один раз на всё приложение.
 */
export function useArrowNav() {
  useEffect(() => {
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);
}
