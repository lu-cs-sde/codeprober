import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import displayHelp from "./displayHelp";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import attachDragToX from "../create/attachDragToX";
import displayAttributeModal from "./displayAttributeModal";
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import createCullingTaskSubmitterFactory from '../../model/cullingTaskSubmitterFactory';
import createStickyHighlightController from '../create/createStickyHighlightController';
import ModalEnv from '../../model/ModalEnv';
import { ListedTreeNode, ListTreeReq, ListTreeRes, NodeLocator, RpcBodyLine } from '../../protocol';
import startEndToSpan from '../startEndToSpan';
import UpdatableNodeLocator, { createMutableLocator } from '../../model/UpdatableNodeLocator';
import { formatAttrBaseName } from './formatAttr';
import layoutTree, { DrawTree } from '../create/layoutTree';

interface Point { x: number; y: number }
type LocatorStr = string;
const nodew = 256 + 128;
const nodeh = 64;
const nodepadx = nodew * 0.05;
const nodepady = nodeh * 0.75;
interface Node {
  ltn: ListedTreeNode;
  dtn: DrawTree<ListedTreeNode>;
  placeholderLoadStared: boolean;
  remoteRefs: { loc: LocatorStr; lbl: string; }[];
  locatorStr: LocatorStr;
  pos: Point;
  children: (Node[]) | 'placeholder';
}
interface ExtraArgs {
  initialTransform?: { [id: string]: number };
  hideTitleBar?: boolean;
}

type AstListDirection = 'downwards' | 'upwards';
const displayAstModal = (env: ModalEnv, modalPos: ModalPosition | null, locator: UpdatableNodeLocator, listDirection: AstListDirection, extraArgs: ExtraArgs = {}) => {
  const queryId = `ast-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  let state: { type: 'ok', data: Node } | { type: 'err', body: RpcBodyLine[] } | null = null;
  let lightTheme = env.themeIsLight();
  const stickyController = createStickyHighlightController(env);

 let fetchState: 'idle' | 'fetching' | 'queued' = 'idle';
  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    delete env.probeWindowStateSavers[queryId];
    delete env.themeChangeListeners[queryId];
    popup.remove();
    env.triggerWindowSave();
    stickyController.cleanup();
  };
  const onResizePtr = {
    callback: () => {},
  };
  const bufferingSaver = createCullingTaskSubmitterFactory(100)();
  const saveAfterTransformChange = () => {
    bufferingSaver.submit(() => { env.triggerWindowSave() });
  };
  const initialTransform = extraArgs.initialTransform;
  const trn = {
    x: initialTransform?.x ?? 1920/2,
    y: initialTransform?.y ?? 0,
    scale: initialTransform?.scale ?? 1,
    width: initialTransform?.width ?? 0,
    height: initialTransform?.height ?? 0,
  };
  let resetTranslationOnRender = !initialTransform;

  const mapListedToNodeCache: { [nodeLocatorStr: string]: Node } = {};
  const placeholderLoadAutoStarts: { [locStr: LocatorStr]: boolean } = {};
  const locatorToStr = (loc: NodeLocator): LocatorStr => loc.steps.map(step => {
    switch (step.type) {
      case 'child': return step.value;
      case 'nta':
        if (!step.value.property.args?.length) {
          return step.value.property.name;
        }
        break;
      default:
        break;
    }
    return JSON.stringify(step);
  }).join(' > ');
  const mapListedToNode = (drawTreeNode: DrawTree<ListedTreeNode>): Node => {
    const src = drawTreeNode.tree;
    const ret: Node = ({
      ltn: src,
      dtn: drawTreeNode,
      placeholderLoadStared: false,
      children: src.children.type === 'children'
        ? drawTreeNode.children.map(mapListedToNode)
        : 'placeholder',
      locatorStr: locatorToStr(src.locator),
      remoteRefs: src.remotes?.map(loc => ({ loc: locatorToStr(loc), lbl: loc.result.label ?? loc.result.type })) ?? [],
      pos: { x: drawTreeNode.x * (nodew + nodepadx), y: drawTreeNode.y * (nodeh + nodepady) },
    });
    mapListedToNodeCache[ret.locatorStr] = ret;
    return ret;
  }
  const permanentHovers: { [locStr: LocatorStr]: boolean } = {};
  const popup = env.showWindow({
    pos: modalPos,
    size: initialTransform?.width && initialTransform?.height ? {
      width: initialTransform.width,
      height: initialTransform.height,
    } : undefined,

    debugLabel: `ast:${locator.get().result.type}`,
    rootStyle: `
      min-width: 24rem;
      min-height: 12rem;
    `,
    onForceClose: cleanup,
    onFinishedMove: () => {
      bufferingSaver.cancel();
      env.triggerWindowSave()
    },
    onOngoingResize: () => onResizePtr.callback(),
    onFinishedResize: () => {
      onResizePtr.callback();
      const size = popup.getSize();
      trn.width = size.width;
      trn.height = size.height;
    },
    resizable: true,
    render: (root, { bringToFront }) => {
      while (root.firstChild) root.firstChild.remove();

      if (!extraArgs.hideTitleBar) {
        root.appendChild(createModalTitle({
          shouldAutoCloseOnWorkspaceSwitch: true,
          renderLeft: (container) => {
            const headType = document.createElement('span');
            headType.innerText = `AST`;

            container.appendChild(headType);
            const spanIndicator = createTextSpanIndicator({
              span: startEndToSpan(locator.get().result.start, locator.get().result.end),
              marginLeft: true,
              onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.get().result.start, locator.get().result.end) : null),
              onClick: stickyController.onClick,
            });
            stickyController.configure(spanIndicator, locator);
            container.appendChild(spanIndicator);
          },
          onClose: () => {
            cleanup();
          },
          extraActions: [
            ...(env.getGlobalModalEnv() === env ? [] :[{
              title: 'Detatch window',
              invoke: () => {
                cleanup();
                displayAstModal(env.getGlobalModalEnv(), null, locator.createMutableClone(), listDirection, {
                  initialTransform,
                  hideTitleBar: extraArgs.hideTitleBar,
                });
              }
            }]),
            {
              title: 'Help',
              invoke: () => {
                displayHelp('ast', () => {});
              }
            },
          ],
        }).element);
      }

      const addSpinner = () => {
        const spinner = createLoadingSpinner();
        spinner.classList.add('absoluteCenter');
        const spinnerWrapper = document.createElement('div');
        spinnerWrapper.style.height = '7rem' ;
        spinnerWrapper.style.display = 'block' ;
        spinnerWrapper.style.position = 'relative';
        spinnerWrapper.appendChild(spinner);
        root.appendChild(spinnerWrapper);
      }

      if (state === null) {
        addSpinner();
        return;
      }

      if (state.type === 'err') {
        if (state.body.length === 0) {
          const text = document.createElement('span');
          text.classList.add('captured-stderr');
          text.innerText = `Failed listing tree`;
          root.appendChild(text);
          return;
        }
        root.appendChild(encodeRpcBodyLines(env, state.body));
      } else {
        // Build UI

        root.style.display = 'flex';
        root.style.flexDirection = 'column';
        root.style.flexGrow = '1';
        root.style.overflow = 'hidden';

        const cv = document.createElement('canvas');
        cv.width = 1920;
        cv.height = 1080;

        const wrapper = document.createElement('div');
        wrapper.appendChild(cv);
        wrapper.style.flexGrow = '1';
        wrapper.style.minWidth = '4rem';
        wrapper.style.minHeight = '4rem';

        const resetBtn = document.createElement('div');

        const resetText = document.createElement('span');
        resetText.innerText = 'Reset View';
        resetBtn.appendChild(resetText);
        resetBtn.style.display = 'none';
        resetBtn.classList.add('ast-view-reset-btn');
        resetBtn.onclick = () => {
          trn.scale = 1;
          trn.x = 1920/2 - rootNode.pos.x - nodew/2;
          trn.y = nodepady;
          renderFrame();
        }

        wrapper.appendChild(resetBtn);
        root.appendChild(wrapper);

        const ctx = cv.getContext('2d');
        if (!ctx) {
          root.appendChild(document.createTextNode(`You browser doesn't seem to support HTML canvas 2D rendering mode.`))
          return;
        }

        cv.style.width = '100%';
        cv.style.height = '100%';
        // cv.width  = cv.offsetWidth;
        // cv.height = cv.offsetHeight;

        cv.onmousedown = (e) => {
          e.stopPropagation();
        };
        cv.style.cursor = 'default';


        // const trn = Array(9).fill(0);

        const lastClick = { x: 0, y: 0 };
        const getScaleY = () => trn.scale * (cv.clientWidth / 1920) / (cv.clientHeight / 1080);
        const clientToWorld = (pt: Point, trnx = trn.x, trny = trn.y, scaleX = trn.scale, scaleY = getScaleY()): Point => {
          const csx = 1920 / cv.clientWidth;
          const x = (pt.x * csx - trnx) / scaleX;

          const csy = 1080 / cv.clientHeight;
          const y = (pt.y * csy - trny) / scaleY;

          // ((pt.x * 1920 / (cv.clientWidth)) - (trnx)) / scaleX == REF
          return { x, y };
        };
        const worldToClient = (pt: Point, trnx = trn.x, trny = trn.y, scaleX = trn.scale, scaleY = getScaleY()): Point => {
          // Opposite equations of clientToWorld
          const x = ((pt.x * scaleX + trnx) * cv.clientWidth) / 1920;
          const y = ((pt.y * scaleY + trny) * cv.clientHeight) / 1080;
          return { x, y };
        };
        const dragInfo = { x: trn.x, y: trn.y }; // , sx: 1, sy: 1 };
        let hoverClick: 'no' | 'maybe' | 'yes' = 'no';
        let holdingShift = false;
        attachDragToX(cv,
          (e) => {
            bringToFront();
            dragInfo.x = trn.x;
            dragInfo.y = trn.y;
            const w = clientToWorld({ x: e.offsetX, y: e.offsetY});
            lastClick.x = w.x;
            lastClick.y = w.y;
            hoverClick = 'maybe';
            holdingShift = e.shiftKey;
            // dragInfo.sx = 1920 / cv.clientWidth;
            // dragInfo.sy = 1080 / cv.clientHeight;
          },
          (dx, dy) => {
            hoverClick = 'no';
            const w = clientToWorld({
              x: dx,
              y: dy,
            }, 0, 0, 1, 1);
            trn.x = dragInfo.x + w.x;
            trn.y = dragInfo.y + w.y;
            // trn.x = dragInfo.x + dx * dragInfo.sx;
            // trn.y = dragInfo.y + dy * dragInfo.sy;
            renderFrame();
            saveAfterTransformChange();
          },
          () => {
            if (hoverClick == 'maybe') {
              hoverClick = 'yes';
              renderFrame();
            }
          });

          cv.addEventListener('wheel', (e) => {
            const ptx = e.offsetX;
            const csx = 1920 / cv.clientWidth;
            const trx1 = trn.x;
            const z1 = trn.scale;
            const delta = (e as any).wheelDelta ?? (e.deltaY * 10);
            const z2 = Math.max(0.1, Math.min(10, trn.scale * (1 + delta / 1000)));
            /*
              -- We want to modify trn.x so that transforming {e.offsetX, e.offsetY} gets the same result before and after zooming.
              -- For trn.x we want this relation to hold:
              (ptx*csx - trx1) / z1 = (ptx*csx - trx2) / z2
              -- All variables are known but trx2. rewrite a bit and we get:
              trx2 = ptx*csx - (z2/z1)*(ptx*csx - trx1)
            */
           trn.x = (ptx*csx - (z2/z1)*(ptx*csx - trx1));

           // Same idea for trn.y
           const csy = 1080 / cv.clientHeight;
           trn.y = (e.offsetY*csy - (z2/z1)*(e.offsetY*csy - trn.y));

            // const w = clientToWorld({ x: e.offsetX, y: e.offsetY });
            // trn.x += (w.x / trn.scale) * e.deltaX / 1000;
            trn.scale = z2;
            renderFrame();
            saveAfterTransformChange();
          });

          let hover: (Point & { ogx: number; ogy: number })|null = null;
          let hasActiveSpanHighlight = false;
          cv.addEventListener('mousemove', e => {
            hover = { ...clientToWorld({ x: e.offsetX, y: e.offsetY }), ogx: e.offsetX, ogy: e.offsetY};
            hoverClick = 'no';
            renderFrame();
          });
          cv.addEventListener('mouseleave', () => {
            hover = null;
            if (hasActiveSpanHighlight) {
              hasActiveSpanHighlight = false;
              env.updateSpanHighlight(null);
            }
            renderFrame();
          })

          let rootNode = state.data;

          if (resetTranslationOnRender) {
            resetTranslationOnRender = false;
            trn.scale = 1;
            trn.x = (1920 -rootNode.dtn.x) / 2;
            trn.y = 0;
            trn.width = root.clientWidth;
            trn.height = root.clientHeight;
          }

          const renderFrame = () => {
          const w = cv.width;
          const h = cv.height;
          let numRenders = 0;

          ctx.resetTransform();
          ctx.fillStyle = getThemedColor(lightTheme, 'probe-result-area');
          ctx.fillRect(0, 0, w, h);
          ctx.translate(trn.x, trn.y);
          ctx.scale(trn.scale, getScaleY());

          cv.style.cursor = 'default';
          let didHighlightSomething = false;
          const hoveredNode: { tgt:  Node | undefined } = { tgt: undefined };
          const renderNode = (node: Node) => { // , ox: number, oy: number

            const renderx = node.pos.x;
            const rendery = node.pos.y;
            const wtc = worldToClient(node.pos);
            const oobPadding = 16;
            if (wtc.y > cv.clientHeight + oobPadding) {
              // Out of bounds, no need to render anything
              return;
            }
            let localOOB = wtc.x > cv.clientWidth + oobPadding;
            if (!localOOB) {
              const rhsWtc = worldToClient({ x: node.pos.x + nodew, y: node.pos.y + nodeh});
              localOOB = rhsWtc.x < -oobPadding || rhsWtc.y < -oobPadding;
            }
            if (!localOOB) {
              ++numRenders;
              if (hover && hover.x >= renderx && hover.x <= (renderx + nodew) && hover.y >= rendery && (hover.y < rendery + nodeh)) {
                hoveredNode.tgt = node;
                ctx.fillStyle = getThemedColor(lightTheme, 'ast-node-bg-hover');
                cv.style.cursor = 'pointer';
                const { start, end, external } = node.ltn.locator.result;
                if (start && end && !external) {
                  didHighlightSomething = true;
                  hasActiveSpanHighlight = true;
                  env.updateSpanHighlight({
                    lineStart: (start >>> 12), colStart: (start & 0xFFF),
                    lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
                  });
                }
                if (hoverClick === 'yes') {
                  hoverClick = 'no';
                  if (holdingShift) {
                    if (permanentHovers[node.locatorStr])  {
                      delete permanentHovers[node.locatorStr];
                    } else {
                      permanentHovers[node.locatorStr] = true;
                    }
                  } else {
                    displayAttributeModal(env.getGlobalModalEnv(), null, createMutableLocator(node.ltn.locator));
                  }
                }
              } else {
                ctx.fillStyle = getThemedColor(lightTheme, 'ast-node-bg');
              }
              ctx.fillRect(renderx, rendery, nodew, nodeh);
              ctx.strokeStyle = getThemedColor(lightTheme, (permanentHovers[node.locatorStr] && node.remoteRefs.length) ? 'syntax-attr' : 'separator');
              if (node.ltn.locator.steps.length > 0 && node.ltn.locator.steps[node.ltn.locator.steps.length - 1].type === 'nta') {
                ctx.setLineDash([5, 5])
                ctx.strokeRect(renderx, rendery, nodew, nodeh);
                ctx.setLineDash([])
              } else {
                ctx.strokeRect(renderx, rendery, nodew, nodeh);
              }
              let fonth = (nodeh * 0.5)|0;
              let renderedName = node.ltn.name;
              renderText: while (true) {
                ctx.font = `${fonth}px sans`;
                const typeTail = (node.ltn.locator.result.label ?? node.ltn.locator.result.type).split('\.').slice(-1)[0];

                const txty = rendery + (nodeh - (nodeh-fonth)*0.5);
                if (renderedName) {
                  const typeTailMeasure = ctx.measureText(`: ${typeTail}`);
                  const nameMeasure = ctx.measureText(renderedName);
                  const totalW = nameMeasure.width + typeTailMeasure.width;
                  if (totalW > nodew) {
                    if (fonth > 16) {
                      fonth = Math.max(16, fonth * 0.9 | 0);
                      continue renderText;
                    }
                    // Else, try shorten the name
                    const shorterName = formatAttrBaseName(renderedName);
                    if (shorterName !== renderedName) {
                      renderedName = shorterName;
                      continue renderText;
                    }
                  }
                  const txtx = renderx + (nodew - totalW)/2;
                  ctx.fillStyle = getThemedColor(lightTheme, 'syntax-variable');
                  ctx.fillText(renderedName, txtx, txty);
                  ctx.fillStyle = getThemedColor(lightTheme, 'syntax-type');
                  // dark: 4EC9B0
                  ctx.fillText(`: ${typeTail}`, txtx + nameMeasure.width, txty);
                } else {
                  ctx.fillStyle = getThemedColor(lightTheme, 'syntax-type');
                  const typeTailMeasure = ctx.measureText(typeTail);
                  if (typeTailMeasure.width > nodew && fonth > 16) {
                    fonth = Math.max(16, fonth * 0.9 | 0);
                    continue renderText;
                  }
                  ctx.fillText(typeTail, renderx + (nodew - typeTailMeasure.width) / 2, txty);
                }
                break;
              }
            }

            const renderChildren = (children: Node[]) => {
              children.forEach((child) => {
                renderNode(child)

                ctx.strokeStyle = getThemedColor(lightTheme, 'separator');
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(renderx + nodew/2, rendery + nodeh);

                const paddedBottomY = rendery + nodeh + nodepady * 0.5;
                ctx.lineTo(renderx + nodew/2, paddedBottomY);

                const { x: chx, y: chy } = child.pos
                ctx.arcTo(chx +  nodew/2, paddedBottomY, chx + nodew/2, chy, nodepady/2);
                ctx.lineTo(chx + nodew/2, chy);
                ctx.stroke();
                ctx.lineWidth = 1;
              });
            }

            if (Array.isArray(node.children)) {
              renderChildren(node.children);
            } else {  // Placeholder
              const locStr = node.locatorStr;

              const msg = `...`;
              const fonth = (nodeh * 0.5)|0;
              ctx.font = `${fonth}px sans`;
              ctx.fillStyle = getThemedColor(lightTheme, 'separator');
              const cx = renderx + nodew/2;
              const cy = rendery + nodeh + nodepady  + fonth;

              ctx.strokeStyle = getThemedColor(lightTheme, 'separator');
              ctx.beginPath();
              ctx.moveTo(cx, rendery + nodeh);
              ctx.lineTo(cx, cy - fonth);
              ctx.stroke();

              const loadExtras = () => {
                placeholderLoadAutoStarts[locStr] = true;
                node.placeholderLoadStared = true;
                setTimeout(renderFrame);
                env.performTypedRpc<ListTreeReq, ListTreeRes>({
                  locator: node.ltn.locator,
                  src: env.createParsingRequestData(),
                  type: 'ListTreeDownwards'
                }).then(res => {
                  if (res.node) {
                    const newChildren = res.node.children;
                    if (newChildren.type === 'placeholder') {
                      console.error('Search budget seems to be set to zero - got placeholder on root of ListTree request');
                      return;
                    }
                    node.ltn.children = newChildren;
                    node.ltn.remotes = res.node.remotes;
                    rootNode = mapListedToNode(layoutTree(rootNode.ltn));

                    setTimeout(renderFrame);
                  } else {
                    console.warn('Failed expanding node..');
                  }
                })
              }
              if (node.placeholderLoadStared) {
                // Already clicked
                ctx.strokeStyle = 'orange';
              } else if (placeholderLoadAutoStarts[node.locatorStr]) {
                loadExtras();
                ctx.strokeStyle = 'orange';
              } else if (hover && Math.hypot(cx - hover.x, cy - hover.y) < fonth) {
                ctx.strokeStyle = 'cyan';
                cv.style.cursor = 'pointer';
                if (hoverClick == 'yes') {
                  hoverClick = 'no';
                  loadExtras();
                }
              }
              const msgMeasure = ctx.measureText(msg);
              ctx.fillText(msg, renderx + (nodew - msgMeasure.width) / 2, cy + fonth * 0.33);
              ctx.beginPath();
              ctx.arc(cx, cy, fonth , 0, Math.PI * 2);
              ctx.stroke();
            }
            return;
          };

          const determineLineAttachPos = (node: Node, otherNode: Node) => {
            const diffX = (otherNode.pos.x) - (node.pos.x);
            const diffY = (otherNode.pos.y) - (node.pos.y);

            let px = node.pos.x + nodew/2;
            let py = node.pos.y;

            px = Math.max(px - nodew/2, Math.min(px + nodew/2, px + diffX/2));
            if (diffY > 1) {
              py += nodeh;
            } else if (diffY < -1) {
              // No change
            } else {
              py += nodeh/2;
            }
            return { x: px, y: py };
          }

          const renderRemoteRefs = (from: Node) =>  {
            from.remoteRefs.forEach(rem => {
              const tgt = mapListedToNodeCache[rem.loc];
              if (!tgt) {
                // Target currently not visible
                return;
              }

              const fromPos = determineLineAttachPos(from, tgt);
              const toPos = determineLineAttachPos(tgt, from);
              ctx.strokeStyle = getThemedColor(lightTheme, 'syntax-attr')
              ctx.lineWidth = 3;
              ctx.setLineDash([5, 5])
              ctx.beginPath();
              ctx.moveTo(fromPos.x, fromPos.y);
              ctx.lineTo(toPos.x, toPos.y);
              ctx.stroke();
              ctx.setLineDash([])
              ctx.lineWidth = 1;

              ctx.save();
              let angle = Math.atan2(toPos.y - fromPos.y, toPos.x - fromPos.x);
              if (angle > 0) {
                angle -= Math.PI * 2;
              }
              const distance = Math.hypot(toPos.y - fromPos.y, toPos.x - fromPos.x);
              const quart = Math.PI/4;

              let pointDir: 'up' | 'right' | 'left' | 'down' = 'right';
              if (angle < -quart && angle >= -quart*3) {
                pointDir = 'up';
              } else if (angle < -quart*3 && angle >= -quart*5) {
                pointDir = 'left';
              } else if (angle < -quart*5 && angle >= -quart*7) {
                pointDir = 'down';
              }
              switch (pointDir) {
                  case 'up':
                  case 'left':
                  ctx.translate(toPos.x, toPos.y);
                  ctx.rotate(angle + Math.PI);
                  break;

                default:
                  ctx.translate(fromPos.x, fromPos.y);
                  ctx.rotate(angle);
              }

              const fonth = (nodeh * 0.3)|0;
              const boxh = fonth * 1.25;
              ctx.font = `${fonth}px sans`;
              const trimmed = formatAttrBaseName(rem.lbl);
              const measure = ctx.measureText(trimmed);

              ctx.translate(distance / 2, 0);
              switch (pointDir) {
                case 'up':
                case 'down':
                  ctx.rotate(-Math.PI/2);
                  break;
              }
              ctx.translate(-measure.width/2, -boxh/2);

              ctx.fillStyle = getThemedColor(lightTheme, 'ast-node-bg');
              ctx.fillRect(-measure.width /4, 0, measure.width * 1.5, boxh)
              ctx.strokeStyle = getThemedColor(lightTheme, 'separator')
              ctx.strokeRect(-measure.width /4, 0, measure.width * 1.5, boxh)

              ctx.fillStyle = getThemedColor(lightTheme, 'syntax-attr')
              const txty = (fonth - (boxh-fonth)*0.5);
              ctx.fillText(trimmed, 0, txty);


              ctx.restore();
            })
          }

          renderNode(rootNode);
          if (!didHighlightSomething) {
            if (hasActiveSpanHighlight) {
              hasActiveSpanHighlight = false;
              env.updateSpanHighlight(null);
            }
          }
          if (hoveredNode.tgt) {
            renderRemoteRefs(hoveredNode.tgt);
          }
          Object.entries(permanentHovers).forEach(([key, val]) => {
            if (val) {
              const perm = mapListedToNodeCache[key];
              if (perm && perm !== hoveredNode.tgt) {
                renderRemoteRefs(perm);
              }
            }
          });

          if (numRenders === 0) {
            resetBtn.style.display = 'block';
          } else {
            resetBtn.style.display = 'none';
          }
        };
        renderFrame();
        onResizePtr.callback = () => {
          renderFrame();
        };
      }

      if (fetchState !== 'idle') {
        const spinner = createLoadingSpinner();
        spinner.classList.add('absoluteCenter');
        root.appendChild(spinner);
      }
    },
  });
  const refresher = env.createCullingTaskSubmitter();
  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {
      locator.adjust(adjusters);
    }
    refresher.submit(() => {
      fetchAttrs();
      popup.refresh();
    });
  };

 const fetchAttrs = () => {
  switch (fetchState) {
    case 'idle': {
      fetchState = 'fetching'
      break;
    }
    case 'fetching': {
      fetchState = 'queued';
      return;
    }
    case 'queued': return;
  }

  env.performTypedRpc<ListTreeReq, ListTreeRes>({
    locator: locator.get(),
    src: env.createParsingRequestData(),
    type: listDirection === 'upwards' ? 'ListTreeUpwards' : 'ListTreeDownwards'
  })
    .then((result) => {
      const refetch = fetchState == 'queued';
      fetchState = 'idle';
      if (refetch) fetchAttrs();

      const parsed = result.node;
      if (!parsed) {
        // root.appendChild(createTitle('err'));
        if (result.body?.length) {
          state = { type: 'err', body: result.body };
          popup.refresh();
          // root.appendChild(encodeRpcBodyLines(env, parsed.body));
          return;
        }
        throw new Error('Unexpected response body "' + JSON.stringify(result) + '"');
      }

      // Handle resp
      if (result.locator) {
        locator.set(result.locator);
      }
      Object.keys(mapListedToNodeCache).forEach(k => delete mapListedToNodeCache[k]);
      state = { type: 'ok', data: mapListedToNode(layoutTree(parsed)) };
      popup.refresh();

    })
    .catch(err => {
      console.warn('Error when loading attributes', err);
      state = { type: 'err', body: [] };
      popup.refresh();
    });
 };
 fetchAttrs();

 env.probeWindowStateSavers[queryId] = (target) => {
  target.push({
    modalPos: popup.getPos(),
    data: {
      type: 'ast',
      locator: locator.get(),
      direction: listDirection,
      transform: { ...trn, },
    },
  });
};
env.themeChangeListeners[queryId] = (light) => {
  lightTheme = light;
  onResizePtr.callback();
};
env.triggerWindowSave();
}

export default displayAstModal;
