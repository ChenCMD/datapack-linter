"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getSafeMessageData = void 0;
function getSafeMessageData(data, type) {
    var _a;
    return (_a = data[type]) !== null && _a !== void 0 ? _a : (data[type] = []);
}
exports.getSafeMessageData = getSafeMessageData;
