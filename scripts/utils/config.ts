/* eslint-disable @typescript-eslint/no-explicit-any */
import { pathAccessible, readFile } from '@spgoding/datapack-language-server';
import { Config, constructConfig, Uri } from '@spgoding/datapack-language-server/lib/types';
import { loadLocale } from '@spgoding/datapack-language-server/lib/locales';
import stripJsonComments from 'strip-json-comments';
import * as jsonc from 'jsonc-parser';
import path from 'path';

/**
 * Get config.
 * This function behaves similarly to the getConfiguration function of the VSCodeAPI.
 */
export async function getConfiguration(configPath: string): Promise<Config> {
    if (!await pathAccessible(configPath)) {
        console.log('Could not access the config file. Use the default config file.');
        return constructConfig({});
    }
    const json = await readFile(configPath);
    const result: { [key: string]: any } = {};
    const obj = jsonc.parse(stripJsonComments(json));
    Object.entries(obj).forEach(([key, value]) => {
        const walk = (cfg: { [key: string]: any }, [head, ...tail]: string[]): void => {
            if (cfg[head] === undefined)
                cfg[head] = {};

            if (tail.length > 0)
                walk(cfg[head], tail);
            else
                cfg[head] = value;
        };
        return walk(result, key.split('.'));
    });
    return constructConfig(result.datapack);
}

export async function readConfig(dir: string): Promise<Config> {
    const configUri = Uri.file(path.resolve(dir, './.vscode/settings.json'));
    const config = await getConfiguration(configUri.fsPath);
    await loadLocale(config.env.language, 'en');
    return config;
}