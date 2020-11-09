import { getCommandTree } from '@spgoding/datapack-language-server/lib/data/CommandTree';
import { requestText } from '@spgoding/datapack-language-server';
import { getVanillaData, VanillaData } from '@spgoding/datapack-language-server/lib/data/VanillaData';
import { ParsingContext, constructContext, CommandTree, VersionInformation, Uri } from '@spgoding/datapack-language-server/lib/types';
import { getJsonSchemas } from '@spgoding/datapack-language-server/lib/data/JsonSchema';
import { SchemaRegistry } from '@spgoding/datapack-language-server/node_modules/@mcschema/core/lib/Registries';
import { ParserCollection } from '@spgoding/datapack-language-server/lib/parsers/ParserCollection';
import { TextDocument } from 'vscode-languageserver-textdocument';
import path from 'path';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { getRootIndex } from '@spgoding/datapack-language-server/lib/services/common';
import { cacheFile, config, roots } from '..';

export const globalStoragePath = path.join(__dirname, '_storage');

let init = false;
let vanillaData: VanillaData;
let commandTree: CommandTree;
export let jsonSchemas: SchemaRegistry;

async function initData(): Promise<void> {
    init = true;
    vanillaData = await getVanillaData(config.env.dataVersion, config.env.dataSource, await getLatestVersions(), globalStoragePath);
    jsonSchemas = await getJsonSchemas(config.env.jsonVersion, vanillaData.Registry);
    commandTree = await getCommandTree(config.env.cmdVersion);
}

export async function getParsingContext(uri: Uri, textDoc: TextDocument): Promise<ParsingContext> {
    if (!init)
        await initData();
    const idResult = IdentityNode.fromRel(uri.fsPath);
    return constructContext({
        blockDefinition: vanillaData?.BlockDefinition,
        cache: cacheFile.cache,
        commandTree,
        config,
        cursor: -1,
        id: idResult?.id,
        jsonSchemas,
        namespaceSummary: vanillaData?.NamespaceSummary,
        nbtdoc: vanillaData?.Nbtdoc,
        parsers: new ParserCollection(),
        registry: vanillaData?.Registry,
        rootIndex: getRootIndex(uri, roots),
        roots: roots,
        textDoc
    });
}

async function getLatestVersions() {
    let ans: VersionInformation | undefined;
    try {
        console.info('[LatestVersions] Fetching the latest versions...');
        const str = await Promise.race([
            requestText('https://launchermeta.mojang.com/mc/game/version_manifest.json'),
            new Promise<string>((_, reject) => {
                setTimeout(() => reject(new Error('Time out!')), 7_000);
            })
        ]);
        const { latest: { release, snapshot }, versions }: { latest: { release: string, snapshot: string }, versions: { id: string }[] } = JSON.parse(str);
        const processedVersion = '1.16.2-pre1';
        const processedVersionIndex = versions.findIndex(v => v.id === processedVersion);
        const processedVersions = processedVersionIndex >= 0 ? versions.slice(0, processedVersionIndex + 1).map(v => v.id) : [];
        ans = (release && snapshot) ? { latestRelease: release, latestSnapshot: snapshot, processedVersions } : undefined;
    } catch (e) {
        console.warn(`[LatestVersions] ${e}`);
    }
    console.info(`[LatestVersions] versionInformation = ${JSON.stringify(ans)}`);
    return ans;
}