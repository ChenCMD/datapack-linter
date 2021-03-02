import { promises as fsp } from 'fs';
import { Checksum } from '../types/Checksum';

export class FileChangeChecker {
    private readonly _nextChecksum: {
        deleted: string[],
        updated: Checksum
    } = { deleted: [], updated: {} };

    constructor(private readonly _checksums: Checksum | undefined) { }

    isChecksumsExists(): boolean {
        return !!this._checksums;
    }

    isFileNewly(file: string): boolean {
        return !this._checksums?.[file];
    }

    isFileNotEqualChecksum(file: string, newChecksum: string, allowChecksumUndefined = true): boolean {
        return (allowChecksumUndefined || !this.isFileNewly(file))
            && this._checksums?.[file] !== newChecksum;
    }

    appendNextChecksum(type: 'deleted', file: string): void;
    appendNextChecksum(type: 'updated', file: string, newChecksum: string): void;
    appendNextChecksum(type: 'deleted' | 'updated', file: string, newChecksum?: string): void {
        if (type === 'updated') this._nextChecksum[type][file] = newChecksum!.toLowerCase();
        if (type === 'deleted') this._nextChecksum[type].push(file);
    }

    async writeChecksumFile(path: string): Promise<void> {
        this._nextChecksum.deleted.forEach(v => delete this._checksums?.[v]);
        return await fsp.writeFile(path, JSON.stringify({ ...this._checksums, ...this._nextChecksum.updated }), { encoding: 'utf8' });
    }
}