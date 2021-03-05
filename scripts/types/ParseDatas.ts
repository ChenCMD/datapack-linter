export interface DocumentData {
    root: string
    rel: string
}

export interface ParsedData {
    lint?: LintData
    define?: string[]
}

export interface LintData {
    messages: string[]
    failCount: FailCount
}

export interface FailCount {
    warning: number
    error: number
}