import { promises as fsp } from 'fs';
import { Checksum } from '../types';

export class FileChangeChecker {
    private readonly _nextChecksum: {
        deleted: string[],
        updated: Checksum
    } = { deleted: [], updated: {} };

    private readonly _bypassFiles: string[] = [];

    constructor(private _checksums: Checksum | undefined) { }

    isChecksumsExists(): boolean {
        return !!this._checksums;
    }

    isFileNewly(file: string): boolean {
        return !this._checksums?.[file];
    }

    isFileNotEqualChecksum(file: string, newChecksum: string | undefined, allowChecksumUndefined = true, allowBypassFiles = true): boolean {
        return (allowBypassFiles && this._bypassFiles.some(v => v === file))
            || ((allowChecksumUndefined || !this.isFileNewly(file)) && this._checksums?.[file] !== newChecksum);
    }

    clearChecksum(): void {
        this._checksums = undefined;
    }

    appendBypassFiles(...files: string[]): void {
        this._bypassFiles.push(...files);
    }

    updateNextChecksum(file: string, newChecksum?: string): void {
        if (newChecksum) this._nextChecksum.updated[file] = newChecksum.toLowerCase();
        else this._nextChecksum.deleted.push(file);
    }

    async writeChecksumFile(path: string): Promise<string> {
        this._nextChecksum.deleted.forEach(v => delete this._checksums?.[v]);
        await fsp.writeFile(path, JSON.stringify({ ...this._checksums, ...this._nextChecksum.updated }), { encoding: 'utf8' });
        return JSON.stringify({ ...this._checksums, ...this._nextChecksum.updated }, undefined, '    ');
    }
}