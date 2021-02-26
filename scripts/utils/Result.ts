/* eslint-disable prefer-template */
import { CacheCategory, CacheType, CacheVisibility, Config, DatapackDocument } from '@spgoding/datapack-language-server/lib/types';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import path from 'path';
import { FailCount } from '../types/Results';

export class Result {
    private _errorCount = 0;
    private _warnCount = 0;
    private _defineMessage: string[] = [];

    constructor(private readonly _rawTestPath: string, private readonly _config: Config) { }

    get defineMessage(): string[] {
        return this._defineMessage;
    }

    get isOutDefine(): boolean {
        return this._rawTestPath !== '';
    }

    addFailCount(failCount: FailCount): void {
        this._errorCount += failCount.error;
        this._warnCount += failCount.warning;
    }

    hasFailCount(): boolean {
        return this._errorCount + this._warnCount === 0;
    }

    getFailCountMessage(): string {
        const errorMul = this._errorCount > 1 ? 's' : '';
        const warningMul = this._warnCount > 1 ? 's' : '';
        return `${this._errorCount} error${errorMul}, ${this._warnCount} warning${warningMul}`;
    }

    appendDefineMessage(parsedData: DatapackDocument, id: IdentityNode, root: string, rel: string): void {
        const test = (visibility: CacheVisibility | CacheVisibility[] | undefined): boolean => {
            if (visibility) {
                if (visibility instanceof Array)
                    return visibility.length ? visibility.some(v => test(v)) : test(undefined);

                const regex = new RegExp(`^${visibility.pattern
                    .replace(/\?/g, '[^:/]')
                    .replace(/\*\*\//g, '.*')
                    .replace(/\*\*/g, '.*')
                    .replace(/\*/g, '[^:/]*')}$`);
                return this._rawTestPath.split(/\r?\n/).some(v => regex.test(v) || regex.test(v.match(/^[^:]+$/) ? `minecraft:${v}` : v));
            }

            const defaultVisibility = this._config.env.defaultVisibility;
            if (typeof defaultVisibility === 'string') {
                if (defaultVisibility === 'private')
                    return test({ type: '*', pattern: id.toString() });

                if (defaultVisibility === 'internal') {
                    const namespace = id.getNamespace();
                    const genVisivility = (pattern: string): CacheVisibility => ({ type: '*', pattern });
                    if (namespace === IdentityNode.DefaultNamespace)
                        return test(genVisivility(namespace));
                    else
                        return test([genVisivility(namespace + ':**'), genVisivility(IdentityNode.DefaultNamespace + ':**')]);
                }
                if (defaultVisibility === 'public')
                    return true;
            }
            return (defaultVisibility instanceof Array && !defaultVisibility.length) ? true : test(defaultVisibility);
        };

        // parse and append region
        let isDefineFind = false;
        const append = (str: string, indent = 0) => this._defineMessage.push(' '.repeat(indent) + str);

        for (const node of parsedData?.nodes ?? []) {
            for (const type of Object.keys(node.cache) as CacheType[]) {
                const category = node.cache[type] as CacheCategory;
                for (const name of Object.keys(category)) {
                    const defines = [...category[name]!.dcl ?? [], ...category[name]!.def?.filter(v => v.end) ?? []];
                    if (defines.some(v => test(v.visibility))) {
                        if (!isDefineFind) {
                            append(`${path.parse(root).name}/${rel.replace(/\\/g, '/')}`);
                            isDefineFind = true;
                        }
                        append(type + ' ' + name, 4);
                    }
                }
            }
        }
    }
}