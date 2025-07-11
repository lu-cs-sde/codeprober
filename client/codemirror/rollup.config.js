import resolve from '@rollup/plugin-node-resolve';
import babel from '@rollup/plugin-babel';
import terser from '@rollup/plugin-terser';

export default [
    {
        input: 'main.js',
        output: {
            file: '../public/codemirror-bundle.js',
            format: 'iife',
            name: 'editor_codemirror',
            sourcemap: false,
            compact: true,
            minifyInternalExports: true,
        },
        external: [
        ],
        plugins: [
            resolve(),
            (process.env.NODE_ENV === 'production' && babel({
                exclude: ['node_modules/**'],
            })),
            terser(), // Comment for easier debugging
        ],
    }
];
