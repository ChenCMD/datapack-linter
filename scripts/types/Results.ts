export interface DocumentData {
    file: string
    rel: string
}

export interface FailCount {
    warning: number
    error: number
}

export function getSafeRecordValue<T extends string | number | symbol, U>(data: Record<T, U[]>, type: T): U[] {
    return data[type] ?? (data[type] = []);
}