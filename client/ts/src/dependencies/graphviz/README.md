These are sources from d3-graphviz: https://www.npmjs.com/package/d3-graphviz Version: 5.1.0, BSD-3-Clause License.

To produce the file `graphviz.ts` the following steps were taken:
1) Check out the source repository
2) Modify rollup.config.js:
    - change output.format to 'es'
    - remove output.globals
    - remove external
3) Build, which produces a giant ".js" file.
4) Rename the file to ".ts", and place it here.
5) Prefix with `// @ts-nocheck` to suppress any errors

All of this is done rather than npm install in order to simplify the website bundling process.
