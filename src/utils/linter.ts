import { CacheFile, Config, SyntaxComponent } from '@spgoding/datapack-language-server/lib/types';
import { StringReader } from '@spgoding/datapack-language-server/lib/utils/StringReader';
import { TextDocument } from 'vscode-languageserver-textdocument';
import { getParsingContext } from './contextGenerator';
import { generateSyntaxComponentParsers } from './pluginLoader';

export async function lintFile(textDoc: TextDocument, cacheFile: CacheFile, config: Config, end = textDoc.getText().length): Promise<SyntaxComponent[]> {
    const ans: SyntaxComponent[] = [];
    const string = textDoc.getText();
    const reader = new StringReader(string, 0, end);
    const ctx = await getParsingContext(textDoc, cacheFile, config);
    const componentParsers = await generateSyntaxComponentParsers(textDoc.languageId);
    const currentLine = () => textDoc.positionAt(reader.cursor).line;
    const finalLine = textDoc.positionAt(end).line;
    let lastLine = -1;
    while (lastLine < currentLine() && currentLine() <= finalLine) {
        const matchedParsers = componentParsers
            .map(v => ({ parser: v, testResult: v.test(reader.clone(), ctx) }))
            .filter(v => v.testResult[0])
            .sort((a, b) => b.testResult[1] - a.testResult[1]);
        if (matchedParsers.length > 0) {
            const result = matchedParsers[0].parser.parse(reader, ctx);
            ans.push(result);
        } else {
            console.error(`[parseSyntaxComponents] No matched parser at [${reader.cursor}] with “${reader.remainingString}”.`);
            break;
        }
        lastLine = currentLine();
        reader.nextLine(textDoc);
    }
    return ans;
}