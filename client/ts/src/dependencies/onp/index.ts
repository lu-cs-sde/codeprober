import {SES_DELETE, SES_COMMON, SES_ADD} from "./data";
import {ArrayResults, TextResults, ResultItem, createTextResults} from "./results";
import {onp} from "./onp";
import {ComparedItem, objectifyArray, objectifyLcs, stringifyArray} from "./array";

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

export interface DiffArray<T extends ComparedItem> {
	distance: number;
	lcs: T[];
	results: ResultItem<T>[];
}
export function diffArray<T extends ComparedItem>(arrayA: T[], arrayB: T[]): DiffArray<T> {
	const [a, b, map] = stringifyArray<T>(arrayA, arrayB);
  const [res, ed] = onp(a, b);
	const results = objectifyArray(arrayA, arrayB, res, map);
	const lcs = objectifyLcs(map, results);

	return {
		distance: ed,
		lcs: lcs,
		results: results
	}
}



