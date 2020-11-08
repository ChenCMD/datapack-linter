import { getCommandTree } from '@spgoding/datapack-language-server/lib/data/CommandTree';
import { requestText } from '@spgoding/datapack-language-server';
import { getVanillaData, VanillaData } from '@spgoding/datapack-language-server/lib/data/VanillaData';
import { ParsingContext, constructContext, CommandTree, VersionInformation, CacheFile, Config } from '@spgoding/datapack-language-server/lib/types';
import { getJsonSchemas } from '@spgoding/datapack-language-server/lib/data/JsonSchema';
import { SchemaRegistry } from '@spgoding/datapack-language-server/node_modules/@mcschema/core/lib/Registries';
import { ParserCollection } from '@spgoding/datapack-language-server/lib/parsers/ParserCollection';
import { TextDocument } from 'vscode-languageserver-textdocument';
import path from 'path';

export const globalStoragePath = path.join(__dirname, '_storage');

let init = false;
let vanillaData: VanillaData;
let commandTree: CommandTree;
let jsonSchemas: SchemaRegistry;

async function initData(): Promise<void> {
    console.time('init data');
    init = true;
    vanillaData = await getVanillaData('latest release', 'GitHub', await getLatestVersions(), globalStoragePath);
    commandTree = await getCommandTree('1.16');
    jsonSchemas = await getJsonSchemas('1.16', vanillaData.Registry);
    console.timeEnd('init data');
}

export async function getParsingContext(textDoc: TextDocument, cacheFile: CacheFile, config: Config): Promise<ParsingContext> {
    if (!init)
        await initData();
    return constructContext({
        commandTree,
        jsonSchemas,
        cursor: -1,
        blockDefinition: vanillaData.BlockDefinition,
        namespaceSummary: vanillaData.NamespaceSummary,
        nbtdoc: vanillaData.Nbtdoc,
        registry: vanillaData.Registry,
        parsers: new ParserCollection(),
        textDoc,
        cache: cacheFile.cache,
        config: config
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