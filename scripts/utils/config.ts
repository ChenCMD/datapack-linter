/* eslint-disable @typescript-eslint/no-explicit-any */
import { pathAccessible, readFile } from '@spgoding/datapack-language-server';
import { Config, constructConfig } from '@spgoding/datapack-language-server/lib/types';
import stripJsonComments from 'strip-json-comments';

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
    const obj = JSON.parse(stripJsonComments(json));
    Object.entries(obj).forEach(([path, value]) => {
        const walk = (key: { [key: string]: any }, [head, ...tail]: string[]): void => {
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