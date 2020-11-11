/* eslint-disable @typescript-eslint/no-explicit-any */
import { Config, constructConfig } from '@spgoding/datapack-language-server/lib/types';
import { pathAccessible, readFile } from '@spgoding/datapack-language-server';

/**
 * Get config.
 * This function behaves similarly to the getConfiguration function of the VSCodeAPI.
 */
export async function getConfiguration(configPath: string): Promise<Config> {
    if (!await pathAccessible(configPath))
        return constructConfig({});
    const json = await readFile(configPath);
    const result: {[key: string]: any} = {};
    const obj = JSON.parse(json);
    Object.entries(obj).forEach(([path, value]) => {
        const walk = (key: {[key: string]: any}, [head, ...tail]: string[]): void => {
            if (key[head] === undefined)
                key[head] = {};

            if (tail.length > 0)
                walk(key[head], tail);
            else
                key[head] = value;
        };
        return walk(result, path.split('.'));
    });
    return constructConfig(result.datapack);
}