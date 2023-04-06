
export type ResultItem<T> = {
	left: T;
	right: T;
	state: -1 | 0 | 1;
}
export function createResultItem<T>(left: T, right: T, state: -1 | 0 | 1): ResultItem<T> {
	return { left, right, state };
}

export type TextResults = Array<ResultItem<string>>;
export function createTextResults(results: Array<ResultItem<string>>): TextResults {

	if (results.length === 0) {
		return [];
	}

	let last = createResultItem(results[0].left, results[0].right, results[0].state);
	let shrink: TextResults = [last];

	results.slice(1).forEach((item) => {
		if (item.state !== last.state) {
			last = createResultItem(item.left, item.right, item.state);
			shrink.push(last);
		} else {
			last.left += item.left;
			last.right += item.right;
		}
	});

	return shrink;
}

export type ArrayResults<T> = Array<ResultItem<T>>;