import { DatapackLanguageService } from '@spgoding/datapack-language-server';
import { Uri } from '@spgoding/datapack-language-server/lib/types';
import path from 'path';

export class DLSGarbageCollector {
    private _analyzedObjectCount = 0;

    constructor(private readonly _service: DatapackLanguageService, private readonly _gcThreshold: number) { }

    gc(force: true): void;
    gc(addAnalyzedObjectCount: number): void;
    gc(countOrForce: number | true): void {
        if (typeof countOrForce === 'number')
            this._analyzedObjectCount += countOrForce;
        if (countOrForce === true || this._gcThreshold <= this._analyzedObjectCount) {
            this._analyzedObjectCount = 0;
            this._service.onDeletedFile(Uri.file(path.join('This', 'way', 'I', 'can', 'illegally', 'clear', 'service.caches')));
        }
    }
}