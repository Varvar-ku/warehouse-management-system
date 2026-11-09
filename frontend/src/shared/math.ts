/** Количества на бэкенде имеют до 4 знаков после запятой. */
export const round4 = (v: number) => Math.round(v * 10000) / 10000;
