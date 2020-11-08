import { readFile } from '@spgoding/datapack-language-server';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { getRel, getTextDocument, getUri, walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { ClientCache, combineCache, DatapackDocument, FileType, isRelIncluded, removeCachePosition, setUpUnit, Uri } from '@spgoding/datapack-language-server/lib/types';
import path from 'path';
import { TextDocument } from 'vscode-languageserver-textdocument';
import { cacheFile, config, roots } from '..';
import { parseDocument } from './parser';

export async function initCache(): Promise<void> {
    await Promise.all(roots.map(root => {
        const dataPath = path.join(root.fsPath, 'data');
        return walkFile(
            root.fsPath,
            dataPath,
            async abs => {
                const uri = getUri(Uri.file(abs).toString());
                await onAddedFile(uri);
            },
            // eslint-disable-next-line require-await
            async (_, rel) => isRelIncluded(rel, config)
        );
    }));
}

async function onAddedFile(uri: Uri): Promise<void> {
    const rel = getRel(uri, roots);
    const result = IdentityNode.fromRel(rel);
    if (!result)
        return;
    const { category, id } = result;
    if (!isRelIncluded(rel, config))
        return;
    removeCachePosition(cacheFile.cache, uri);
    await combineCacheOfNodes(uri, category, id);
}

async function combineCacheOfNodes(uri: Uri, type: FileType, id: IdentityNode) {
    const { doc, textDoc } = await getDocuments(uri);
    if (doc && textDoc) {
        const cacheOfNodes: ClientCache = {};
        for (const node of doc.nodes)
            combineCache(cacheOfNodes, node.cache, { uri, getPosition: offset => textDoc.positionAt(offset) });
        combineCache(cacheFile.cache, cacheOfNodes);
    }
    const unit = setUpUnit(cacheFile.cache, type, id);
    if (!(unit.def = unit.def ?? []).find(p => p.uri === uri.toString()))
        (unit.def = unit.def ?? []).push({ uri: uri.toString(), start: 0, end: 0, startLine: 0, startChar: 0, endLine: 0, endChar: 0 });

}

async function getDocuments(uri: Uri): Promise<{ doc: DatapackDocument | undefined, textDoc: TextDocument | undefined }> {
    try {
        const langID = getLangID(uri);
        if (langID === 'nbt') return { doc: undefined, textDoc: undefined };
        const getText = async () => await readFile(uri.fsPath);
        const textDoc = await getTextDocument({ uri, langID, version: null, getText });
        return { doc: await parseDocument(textDoc), textDoc };
    } catch (e) {
        console.error(`[getDocuments] for ${uri} `, e);
    }
    return { doc: undefined, textDoc: undefined };
}

function getLangID(uri: Uri): 'json' | 'mcfunction' | 'nbt' {
    if (uri.fsPath.endsWith('.json') || uri.fsPath.endsWith('.mcmeta'))
        return 'json';
    else if (uri.fsPath.endsWith('.nbt'))
        return 'nbt';
    else
        return 'mcfunction';
}