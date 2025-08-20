import {createResultItem, ResultItem} from "./results";
import {SES_ADD, SES_COMMON, SES_DELETE} from "./data";

export type CodeMap = {
	pointer: number;
	forward: {
		[key: string]: string;
	},
	backward: {
		[key: string]: string;
	}
}
export interface ComparedItem {
	toString(): string;
}
export function stringifyArray<T extends ComparedItem>(a: T[], b: T[]): [string, string, CodeMap] {
	const map: CodeMap = { forward: {}, backward: {}, pointer: 1 };

	const textA = a.map((item) => determineCode(map, item)).join("");
	const textB = b.map((item) => determineCode(map, item)).join("");

	return [textA, textB, map];
}
export function objectifyArray<T extends ComparedItem>(arrayA: T[], arrayB: T[], res: Array<ResultItem<string>>, map: CodeMap): Array<ResultItem<T>> {
	const results = res.map((r) => createResultItem(map.backward[r.left], map.backward[r.right], r.state)) as Array<ResultItem<any>>;

	results
		.filter(filter([SES_COMMON, SES_DELETE]))
		.forEach((item, index) => setData(item, arrayA[index], -1));

	results
		.filter(filter([SES_COMMON, SES_ADD]))
		.forEach((item, index) => setData(item, arrayB[index], 1));

	return results;
}

export function objectifyLcs<T extends ComparedItem>(map: CodeMap, res: Array<ResultItem<T>>): Array<T> {
	return res.filter((item) => {
		return item.state === SES_COMMON;
	}).map((item) => {
		return item.right;
	});
}

function determineCode<T extends ComparedItem>(map: CodeMap, item: T): string {
	let id = item.toString();
	let code = map.forward[id];

	if (!code) {
		code = String.fromCharCode(map.pointer);
		map.forward[id] = code;
		map.backward[code] = id;
		map.pointer++;
	}

	return code;
}

function filter<T>(what: Array<number>): (item: ResultItem<T>) => boolean {
	return (item) => {
		return what.indexOf(item.state) >= 0;
	}
}

function setData<T>(item: ResultItem<T>, data: T, side: -1 | 1) {
	switch (true) {
		case item.state === SES_DELETE:
		case item.state === SES_ADD:
			item.left = item.right = data;
			break;
		case item.state === SES_COMMON && side === -1:
			item.left = data;
			break;
		case item.state === SES_COMMON && side === 1:
			item.right = data;
			break;
		default:
			break;
	}
}
