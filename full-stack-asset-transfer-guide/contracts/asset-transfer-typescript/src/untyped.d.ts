declare module 'json-stringify-deterministic' {
    interface Options {
        space?: string;
        cycles?: boolean;
        replacer?: (k, v) => v;
        stringify?: typeof JSON.stringify;
    }

    export default function stringify(o: unknown, options?: Options): string;
}
