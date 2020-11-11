"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    Object.defineProperty(o, k2, { enumerable: true, get: function() { return m[k]; } });
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.outputMessage = exports.getMessageData = void 0;
const vscode_json_languageservice_1 = require("vscode-json-languageservice");
const core = __importStar(require("@actions/core"));
const path_1 = __importDefault(require("path"));
function getMessageData(parseData, id, document, root, rel) {
    const title = `${id} (${path_1.default.parse(root.fsPath).name}/${rel})`;
    const messages = [];
    for (const node of parseData?.nodes ?? []) {
        if (node.errors.length === 0) // Success
            continue;
        // Failed
        const result = node.errors
            .filter(err => err.severity < 3)
            .map(err => err.toDiagnostic(document))
            .sort((errA, errB) => errA.range.start.line - errB.range.start.line)
            .map(err => {
            const pos = err.range.start;
            const paddingedLine = `   ${pos.line + 1}`.slice(-4);
            const paddingedChar = (`${pos.character + 1}     `).slice(0, 5);
            const humanReadbleSaverity = err.severity === vscode_json_languageservice_1.DiagnosticSeverity.Error ? 'Error  ' : 'Warning';
            return {
                message: `${paddingedLine}:${paddingedChar} ${humanReadbleSaverity} ${err.message}`,
                severity: err.severity ?? vscode_json_languageservice_1.DiagnosticSeverity.Warning
            };
        });
        messages.push(...result);
    }
    return { title, messages };
}
exports.getMessageData = getMessageData;
function outputMessage(results) {
    const failCount = { warning: 0, error: 0 };
    for (const type of Object.keys(results)) {
        // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
        for (const result of results[type].sort((a, b) => a.title > b.title ? 1 : -1)) {
            if (result.messages.length === 0) {
                core.info(`\u001b[92m✓\u001b[39m  ${result.title}`);
                continue;
            }
            core.info(`\u001b[91m✗\u001b[39m  ${result.title}`);
            for (const out of result.messages) {
                if (out.severity === vscode_json_languageservice_1.DiagnosticSeverity.Error) {
                    failCount.error++;
                    core.info(`   ${out.message}`);
                }
                else {
                    failCount.warning++;
                    core.info(` ${out.message}`);
                }
            }
        }
    }
    return failCount;
}
exports.outputMessage = outputMessage;
