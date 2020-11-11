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
const types_1 = require("@spgoding/datapack-language-server/lib/types");
const common_1 = require("@spgoding/datapack-language-server/lib/services/common");
const PluginLoader_1 = require("@spgoding/datapack-language-server/lib/plugins/PluginLoader");
const datapack_language_server_1 = require("@spgoding/datapack-language-server");
const nodes_1 = require("@spgoding/datapack-language-server/lib/nodes");
const locales_1 = require("@spgoding/datapack-language-server/lib/locales");
const core = __importStar(require("@actions/core"));
const path_1 = __importDefault(require("path"));
const utils_1 = require("./utils");
const Results_1 = require("./types/Results");
const dir = process.cwd();
console.log('::add-matcher::./matcher.json');
lint();
async function lint() {
    // log group start
    core.startGroup('init log');
    console.log(`dir: ${dir}`);
    // initialize DatapackLanguageService
    const capabilities = types_1.getClientCapabilities({ workspace: { configuration: true, didChangeConfiguration: { dynamicRegistration: true } } });
    const service = new datapack_language_server_1.DatapackLanguageService({
        capabilities,
        fetchConfig,
        globalStoragePath: path_1.default.join(dir, '_storage'),
        plugins: await PluginLoader_1.PluginLoader.load()
    });
    service.init();
    const dirUri = types_1.Uri.file(dir);
    service.roots.push(...await utils_1.findDatapackRoots(dirUri, await service.getConfig(dirUri)));
    await utils_1.updateCacheFile(service);
    // Lint Region
    const results = {};
    await Promise.all(service.roots.map(async (root) => await common_1.walkFile(root.fsPath, root.fsPath, async (file, rel) => {
        var _a, _b, _c;
        // language check region
        const langID = (_b = (_a = file.match(/(?<=\.).*$/)) === null || _a === void 0 ? void 0 : _a.pop()) !== null && _b !== void 0 ? _b : '';
        if (!(langID === 'mcfunction' || langID === 'json'))
            return;
        // parsing data
        const text = await datapack_language_server_1.readFile(file);
        const textDoc = await common_1.getTextDocument({ uri: types_1.Uri.file(file), langID, version: null, getText: async () => text });
        const parseData = await service.parseDocument(textDoc);
        // get IdentityNode
        const { id, category } = (_c = nodes_1.IdentityNode.fromRel(rel)) !== null && _c !== void 0 ? _c : {};
        // undefined check
        if (!parseData || !id || !category)
            return;
        // pushing message
        Results_1.getSafeMessageData(results, category).push(utils_1.getMessageData(parseData, id, textDoc, root, rel));
    }, async (_, rel) => types_1.isRelIncluded(rel, await service.getConfig(root)))));
    // log group end
    core.endGroup();
    // message output
    const failCount = utils_1.outputMessage(results);
    // last message output
    if (failCount.error + failCount.warning === 0) {
        core.info('Check successful');
    }
    else {
        const errorMul = failCount.error > 1 ? 's' : '';
        const warningMul = failCount.warning > 1 ? 's' : '';
        core.info(`Check failed (${failCount.error} error${errorMul}, ${failCount.warning} warning${warningMul})`);
        process.exitCode = core.ExitCode.Failure;
    }
}
async function fetchConfig() {
    const configUri = types_1.Uri.file(path_1.default.resolve(dir, './.vscode/settings.json'));
    const config = await utils_1.getConfiguration(configUri.fsPath);
    await locales_1.loadLocale(config.env.language, 'en');
    return config;
}
