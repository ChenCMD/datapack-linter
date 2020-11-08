export { };

declare global {
    interface Array<T> {
        flat<U>(func: (x: T) => U[]): U[]
    }
}

Array.prototype.flat = function <T, U>(func: (x: T) => U[]): U[] {
    return (<U[]>[]).concat(...(this as T[]).map(func));
};