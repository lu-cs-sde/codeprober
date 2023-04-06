import {SES_DELETE, SES_COMMON, SES_ADD} from "./data";
import {ArrayResults, TextResults, ResultItem, createTextResults} from "./results";
import {onp} from "./onp";
import {objectifyArray, objectifyLcs, stringifyArray} from "./array";

export {
	SES_DELETE,
	SES_COMMON,
	SES_ADD,
	//types
	ArrayResults,
	ResultItem
}

//## TEXT

export interface DiffText {
	distance: number;
	lcs: string;
	results: TextResults;
}
export function diffText(a: string, b: string): DiffText {
	const [results, ed, lcs] = onp(a, b);

	return {
		distance: ed,
		lcs: lcs,
		results: createTextResults(results)
	}
}

//## ARRAY

export interface DiffArray<T> {
	distance: number;
	lcs: Array<T>;
	results: ArrayResults<T>;
}
export function diffArray<T>(arrayA: Array<T>, arrayB: Array<T>): DiffArray<T> {
	const [a, b, map] = stringifyArray(arrayA, arrayB);
	const [res, ed] = onp(a, b);
	const results = objectifyArray(arrayA, arrayB, res, map);
	const lcs = objectifyLcs(map, results);

	return {
		distance: ed,
		lcs: lcs,
		results: results
	}
}



