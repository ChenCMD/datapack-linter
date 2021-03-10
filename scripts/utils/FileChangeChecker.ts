import { Checksum } from '../types';
import { promises as fsp } from 'fs';

export class FileChangeChecker {
    private readonly _nextChecksum: {
        deleted: string[],
        updated: Checksum
    } = { deleted: [], updated: {} };

    private readonly _forceTrueChecksums: string[] = [];

    constructor(private _checksums: Checksum | undefined) { }

    isChecksumsExists(): boolean {
        return !!this._checksums;
    }

    isFileNewly(file: string): boolean {
        return !this._checksums?.[file];
    }

    isFileNotEqualChecksum(file: string, newChecksum: string | undefined, allowChecksumUndefined = true): boolean {
        return this._forceTrueChecksums.some(v => v === file)
            || ((allowChecksumUndefined || !this.isFileNewly(file)) && this._checksums?.[file] !== newChecksum);
    }

    clearChecksum(): void {
        this._checksums = undefined;
    }

    appendForceTrueChecksums(...files: string[]): void {
        this._forceTrueChecksums.push(...files);
    }

    appendNextChecksum(type: 'deleted', file: string): void;
    appendNextChecksum(type: 'updated', file: string, newChecksum: string): void;
    appendNextChecksum(type: 'deleted' | 'updated', file: string, newChecksum?: string): void {
        if (type === 'updated') this._nextChecksum[type][file] = newChecksum!.toLowerCase();
        if (type === 'deleted') this._nextChecksum[type].push(file);
    }

    async writeChecksumFile(path: string): Promise<string> {
        this._nextChecksum.deleted.forEach(v => delete this._checksums?.[v]);
        await fsp.writeFile(path, JSON.stringify({ ...this._checksums, ...this._nextChecksum.updated }), { encoding: 'utf8' });
        return JSON.stringify({ ...this._checksums, ...this._nextChecksum.updated }, undefined, '    ');
    }
}