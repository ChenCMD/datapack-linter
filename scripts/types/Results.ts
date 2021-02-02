import { CacheType, FileType } from '@spgoding/datapack-language-server/lib/types';
import { DiagnosticSeverity } from 'vscode-json-languageservice';

export type LintingData<T> = {
    [type in FileType]?: Output<T>[]
};

export interface Output<T> {
    title: string
    messages: T[]
}

export interface ErrorData {
    severity: DiagnosticSeverity
    message: string
}

export interface DefineData {
    type: CacheType
    name: string
}

export interface FailCount {
    warning: number
    error: number
}

export function getSafeMessageData<T>(data: LintingData<T>, type: FileType): Output<T>[] {
    return data[type] ?? (data[type] = []);
}