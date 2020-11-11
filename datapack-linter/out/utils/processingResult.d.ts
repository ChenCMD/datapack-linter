import { DatapackDocument, Uri } from '@spgoding/datapack-language-server/lib/types';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { TextDocument } from 'vscode-json-languageservice';
import { MessageData, LintingData, FailCount } from '../types/Results';
export declare function getMessageData(parseData: DatapackDocument, id: IdentityNode, document: TextDocument, root: Uri, rel: string): MessageData;
export declare function outputMessage(results: LintingData): FailCount;
