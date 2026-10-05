// UnknownUnitException.java
package ru.warehouse.warehouse.placement;

/** Единица длины не найдена в таблице коэффициентов. */
public class UnknownUnitException extends RuntimeException {
    public UnknownUnitException(String message) {
        super(message);
    }
}