import {position, Position, SES_ADD, SES_COMMON, SES_DELETE} from "./data";
import {createResultItem, ResultItem} from "./results";

export type EntryInfo = {
	a: string;
	b: string;
	m: number;
	n: number;
	reverse: boolean;
	offset: number;
}

function createInfo(a: string, b: string): EntryInfo {
	//switch sides
	if (a.length >= b.length) {
		return {
			a: b,
			b: a,
			m: b.length,
			n: a.length,
			reverse: true,
			offset: b.length + 1
		};
	}

	return {
		a: a,
		b: b,
		m: a.length,
		n: b.length,
		reverse: false,
		offset: a.length + 1
	};
}

export function onp(textA: string, textB: string): [Array<ResultItem<string>>, number, string] {
	const [epc, ed] = positions(textA, textB);
	const [result, lcs] = sequence(textA, textB, epc);
	return [result, ed, lcs];
}

function positions(textA: string, textB: string): [Array<Position>, number] {
	const {n, m, offset} = createInfo(textA, textB);
	const path: Array<number> = [];
	const pos: Array<Position> = [];

	const delta = n - m;
	const size = m + n + 3;
	const fp: any = {};

	for (let i = 0; i < size; i++) {
		fp[i] = SES_DELETE;
		path[i] = SES_DELETE;
	}

	let p = SES_DELETE;
	do {
		++p;
		for (let k = -p; k <= delta - 1; k++) {
			fp[k + offset] = snake(textA, textB, path, pos, k, fp[k - 1 + offset] + 1, fp[k + 1 + offset]);
		}
		for (let k = delta + p; k >= delta + 1; k--) {
			fp[k + offset] = snake(textA, textB, path, pos, k, fp[k - 1 + offset] + 1, fp[k + 1 + offset]);
		}
		fp[delta + offset] = snake(textA, textB, path, pos, delta, fp[delta - 1 + offset] + 1, fp[delta + 1 + offset]);

	} while (fp[delta + offset] !== n);

	let ed = delta + 2 * p;
	let epc: Array<Position> = [];
	let r: number = path[delta + offset];
	while (r !== SES_DELETE) {
		epc[epc.length] = position(pos[r].x, pos[r].y, null);
		r = pos[r].k!;
	}

	return [epc, ed];
}

function sequence(textA: string, textB: string, epc: Array<Position>): [Array<ResultItem<string>>, string] {
	const {a, b, reverse} = createInfo(textA, textB);
	const changes: Array<ResultItem<string>> = [];

	let y_idx = 1;
	let x_idx = 1;
	let py_idx = 0;
	let px_idx = 0;

	let lcs = "";

	for (let i = epc.length - 1; i >= 0; i--) {
		while(px_idx < epc[i].x || py_idx < epc[i].y) {

			if (epc[i].y - epc[i].x > py_idx - px_idx) {
				if (reverse) {
					changes[changes.length] = createResultItem(b[py_idx], b[py_idx], SES_DELETE);
				} else {
					changes[changes.length] = createResultItem(b[py_idx], b[py_idx], SES_ADD);
				}
				++y_idx;
				++py_idx;

			} else if (epc[i].y - epc[i].x < py_idx - px_idx) {
				if (reverse) {
					changes[changes.length] = createResultItem(a[px_idx], a[px_idx], SES_ADD);
				} else {
					changes[changes.length] = createResultItem(a[px_idx], a[px_idx], SES_DELETE);
				}
				++x_idx;
				++px_idx;

			} else {
				changes[changes.length] = createResultItem(a[px_idx], b[py_idx], SES_COMMON);
				lcs += a[px_idx];
				++x_idx;
				++y_idx;
				++px_idx;
				++py_idx;
			}
		}
	}

	return [changes, lcs];
}

function snake(textA: string, textB: string, path: Array<number>, pos: Array<Position>, k: number, p: number, pp: number): number {
	const {a, b, n, m, offset} = createInfo(textA, textB);
	const r = p > pp ? path[k - 1 + offset] : path[k + 1 + offset];

	let y = Math.max(p, pp);
	let x = y - k;

	while (x < m && y < n && a[x] === b[y]) {
		++x;
		++y;
	}

	path[k + offset] = pos.length;
	pos[pos.length] = position(x, y, r);

	return y;
}
