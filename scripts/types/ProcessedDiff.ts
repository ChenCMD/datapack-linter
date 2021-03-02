import { context } from '@actions/github/lib/utils';
import { getOctokit } from '@actions/github';
import core from '@actions/core';

export type DiffStatus = 'added' | 'modified' | 'removed' | 'renamed';

export interface ProcessedDiff {
    filename: string
    status: DiffStatus
}

export async function getDiffFiles(): Promise<ProcessedDiff[] | undefined> {
    const _getCompareFiles = async (base: string, head: string) => {
        const client = getOctokit(core.getInput('token', { required: true }));
        const differences = await client.repos.compareCommits({
            owner: context.repo.owner,
            repo: context.repo.repo,
            base,
            head
        });
        const result: ProcessedDiff[] = [];
        for (const { filename, status } of differences.data.files) {
            if (status === 'added' || status === 'modified' || status === 'removed' || status === 'renamed')
                result.push({ filename, status });
            else
                core.error(`Observed an unexpected compare status: ${status}. This may be resolved by raising an issue in the datapack-linter repository and reporting it.`);
        }
        return result;
    };

    switch (context.eventName) {
        case 'push':
            return _getCompareFiles(context.payload.before, context.payload.after);
        case 'pull_request':
            return _getCompareFiles(context.payload.pull_request?.base?.sha, context.payload.pull_request?.head?.sha);
        default:
            core.error(`Trigger: "${context.eventName}" is not supported in datapack-linter.`);
            return undefined;
    }
}

export function isDiffInculuded(rel: string, compare: ProcessedDiff[] | undefined, type: DiffStatus[]): boolean {
    return !compare || compare.filter(v => type.some(v2 => v.status === v2)).some(v => rel === v.filename);
}