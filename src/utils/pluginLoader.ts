import { Plugin } from '@spgoding/datapack-language-server/lib/plugins';
import { Contributions, LanguageConfig } from '@spgoding/datapack-language-server/lib/plugins/LanguageConfigImpl';
import { PluginLoader } from '@spgoding/datapack-language-server/lib/plugins/PluginLoader';
import { SyntaxComponentParser } from '@spgoding/datapack-language-server/lib/types';

let init = false;
let plugins: Map<string, Plugin>;
let contributions: Contributions;
let languageConfigs: Map<string, LanguageConfig>;

async function initPlugin(): Promise<void> {
    console.time('init plugin');
    init = true;
    plugins = await PluginLoader.load();
    contributions = await PluginLoader.getContributions(plugins);
    languageConfigs = await PluginLoader.getLanguageConfigs(plugins, contributions);
    console.timeEnd('init plugin');
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function generateSyntaxComponentParsers(language: string): Promise<SyntaxComponentParser<any>[]> {
    if (!init)
        await initPlugin();
    return languageConfigs?.get(language)?.syntaxComponentParsers ?? [];
}