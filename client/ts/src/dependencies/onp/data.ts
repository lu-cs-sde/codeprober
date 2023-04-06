
export const SES_DELETE = -1;
export const SES_COMMON = 0;
export const SES_ADD    = 1;

export type Position = {
	x: number;
	y: number;
	k: number | null;
}
export function position(x: number, y: number, k: number | null): Position {
	return { x, y, k };
}