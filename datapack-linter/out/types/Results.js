"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getSafeMessageData = void 0;
function getSafeMessageData(data, type) {
    return data[type] ?? (data[type] = []);
}
exports.getSafeMessageData = getSafeMessageData;
