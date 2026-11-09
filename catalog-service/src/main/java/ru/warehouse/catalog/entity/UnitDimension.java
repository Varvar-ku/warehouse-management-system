package ru.warehouse.catalog.entity;

/** Тип величины единицы измерения. Нужен, чтобы длине нельзя было задать «килограммы» (M5). */
public enum UnitDimension { LENGTH, MASS, COUNT, OTHER }
