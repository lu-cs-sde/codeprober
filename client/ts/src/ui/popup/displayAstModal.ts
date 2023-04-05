import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import showWindow from "../create/showWindow";
import displayHelp from "./displayHelp";
import adjustLocator from "../../model/adjustLocator";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import attachDragToX from "../create/attachDragToX";
import displayAttributeModal from "./displayAttributeModal";
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import createCullingTaskSubmitterFactory from '../../model/cullingTaskSubmitterFactory';
import createStickyHighlightController from '../create/createStickyHighlightController';
import ModalEnv from '../../model/ModalEnv';
import listTree, { ListedTreeNode } from '../../rpc/listTree';

interface Point { x: number; y: number }
interface Node extends ListedTreeNode {
  boundingBox?: Point;
  children: (Node[]) | { type: 'placeholder', num: number };
}
type AstListDirection = 'downwards' | 'upwards';
const displayAstModal = (env: ModalEnv, modalPos: ModalPosition | null, locator: NodeLocator, listDirection: AstListDirection, initialTransform?: { [id: string]: number }) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
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
  const trn = {
    x: initialTransform?.x ?? 1920/2,
    y: initialTransform?.y ?? 0,
    scale: initialTransform?.scale ?? 1,
    width: initialTransform?.width ?? 0,
    height: initialTransform?.height ?? 0,
  };
  let resetTranslationOnRender = !initialTransform;
  const popup = showWindow({
    pos: modalPos,
    size: initialTransform?.width && initialTransform?.height ? {
      width: initialTransform.width,
      height: initialTransform.height,
    } : undefined,
    rootStyle: `
      min-width: 24rem;
      min-height: 12rem;
      80vh;
    `,
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
      // root.innerText = 'Loading..';

      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const headType = document.createElement('span');
          headType.innerText = `AST`;

          container.appendChild(headType);
          const spanIndicator = createTextSpanIndicator({
            span: startEndToSpan(locator.result.start, locator.result.end),
            marginLeft: true,
            onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
            onClick: stickyController.onClick,
          });
          stickyController.configure(spanIndicator, locator);
          container.appendChild(spanIndicator);
        },
        onClose: () => {
          cleanup();
        },
        extraActions: [
          {
            title: 'Help',
            invoke: () => {
              displayHelp('ast', () => {});
            }
          },
        ],
      }).element);

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
        // wrapper.style.width = '100vw';
        // wrapper.style.height = '100vh';
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
          //
          return { x, y };
        };
        const dragInfo = { x: trn.x, y: trn.y }; // , sx: 1, sy: 1 };
        let hoverClick: 'no' | 'maybe' | 'yes' = 'no';
        attachDragToX(cv,
          (e) => {
            bringToFront();
            dragInfo.x = trn.x;
            dragInfo.y = trn.y;
            const w = clientToWorld({ x: e.offsetX, y: e.offsetY});
            lastClick.x = w.x;
            lastClick.y = w.y;
            hoverClick = 'maybe';
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
            const z2 = Math.max(0.1, Math.min(10, trn.scale + e.deltaY / 100.0));
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

          let hover: Point|null = null;
          let hasActiveSpanHighlight = false;
          cv.addEventListener('mousemove', e => {
            hover = clientToWorld({ x: e.offsetX, y: e.offsetY });
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

          const rootNode = state.data;

          const nodew = 256 + 128;
          const nodeh = 64;
          const nodepadx = nodew * 0.05;
          const nodepady = nodeh * 0.75;
          const measureBoundingBox = (node: Node): Point => {
            if (node.boundingBox) { return node.boundingBox ; }
            let bb: Point = { x: nodew, y: nodeh};

            if (Array.isArray(node.children)) {
              let childW = 0;
              node.children.forEach((child, childIdx) => {
                const childBox = measureBoundingBox(child);
                if (childIdx >= 1) {
                  childW += nodepadx;
                }
                childW += childBox.x;
                bb.y = Math.max(bb.y, nodeh + nodepady + childBox.y);
              });
              bb.x = Math.max(bb.x, childW);
            }
            node.boundingBox = bb;
            return bb;
          };
          const rootBox = measureBoundingBox(rootNode);
          if (resetTranslationOnRender) {
            resetTranslationOnRender = false;
            trn.scale = 1;
            trn.x = (1920 -rootBox.x) / 2;
            trn.y = 0;
          }

          const renderFrame = () => {
          const w = cv.width;
          const h = cv.height;

          ctx.resetTransform();
          ctx.fillStyle = getThemedColor(lightTheme, 'probe-result-area');
          ctx.fillRect(0, 0, w, h);
          // console.log('render', trn.x, ' + ', w,  '|', trn.x * 1920 / w, cv.clientWidth, cv);
          // ctx.translate(trn.x * 1920 / cv.clientWidth, trn.y * 1080 / cv.clientHeight);
          ctx.translate(trn.x, trn.y);
          ctx.scale(trn.scale, getScaleY());

          cv.style.cursor = 'default';
          let didHighlightSomething = false;
          const renderNode = (node: Node, ox: number, oy: number) => {
            const nodeBox = measureBoundingBox(node);
            const renderx = ox + (nodeBox.x - nodew) / 2;
            const rendery = oy;
            if (hover && hover.x >= renderx && hover.x <= (renderx + nodew) && hover.y >= rendery && (hover.y < rendery + nodeh)) {
              ctx.fillStyle = getThemedColor(lightTheme, 'ast-node-bg-hover');
              cv.style.cursor = 'pointer';
              const { start, end, external } = node.locator.result;
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
                displayAttributeModal(env, null, node.locator);
              }
            } else {
              ctx.fillStyle = getThemedColor(lightTheme, 'ast-node-bg');
            }
            ctx.fillRect(renderx, rendery, nodew, nodeh);

            ctx.strokeStyle = getThemedColor(lightTheme, 'separator');
            if (node.locator.steps.length > 0 && node.locator.steps[node.locator.steps.length - 1].type === 'nta') {
              ctx.setLineDash([5, 5])
              ctx.strokeRect(renderx, rendery, nodew, nodeh);
              ctx.setLineDash([])
            } else {
              ctx.strokeRect(renderx, rendery, nodew, nodeh);
            }

            // ctx.fillStyle = `black`;
            let fonth = (nodeh * 0.5)|0;
            renderText: while (true) {
              ctx.font = `${fonth}px sans`;
              const typeTail = (node.locator.result.label ?? node.locator.result.type).split('\.').slice(-1)[0];

              const txty = rendery + (nodeh - (nodeh-fonth)*0.5);
              if (node.name) {
                const typeTailMeasure = ctx.measureText(`: ${typeTail}`);
                const nameMeasure = ctx.measureText(node.name);
                const totalW = nameMeasure.width + typeTailMeasure.width;
                if (totalW > nodew && fonth > 16) {
                  fonth = Math.max(16, fonth * 0.9 | 0);
                  continue renderText;
                }
                const txtx = renderx + (nodew - totalW)/2;
                ctx.fillStyle = getThemedColor(lightTheme, 'syntax-variable');
                ctx.fillText(node.name, txtx, txty);
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

            if (!Array.isArray(node.children)) {
              if (node.children?.type == 'placeholder') {
                // More children available
                // console.log('placeholder:', node.children);
                const msg = `᠁`;
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

                if (hover && Math.hypot(cx - hover.x, cy - hover.y) < fonth) {
                  ctx.strokeStyle = 'cyan';
                  cv.style.cursor = 'pointer';
                  if (hoverClick == 'yes') {
                    hoverClick = 'no';
                    displayAstModal(env, null, node.locator, 'downwards');
                  }
                }
                const msgMeasure = ctx.measureText(msg);
                ctx.fillText(msg, renderx + (nodew - msgMeasure.width) / 2, cy + fonth * 0.33);
                ctx.beginPath();
                ctx.arc(cx, cy, fonth , 0, Math.PI * 2);
                ctx.stroke();


              }
              return;
            }

            // const numCh = node.children.length;
            // const off = (numCh - 1) * (nodew + nodepad) / 2;

            let childOffX = 0;
            const childOffY = nodeh + nodepady;

            node.children.forEach((child, childIdx) => {
              const chbb = measureBoundingBox(child);
              if (childIdx >= 1) {
                childOffX += nodepadx;
              }
              renderNode(child, ox + childOffX, oy + childOffY);

              ctx.strokeStyle = getThemedColor(lightTheme, 'separator');
              ctx.lineWidth = 2;
              ctx.beginPath(); // Start a new path
              ctx.moveTo(renderx + nodew/2, rendery + nodeh);

              const paddedBottomY = rendery + nodeh + nodepady * 0.5;
              ctx.lineTo(renderx + nodew/2, paddedBottomY);
              // ctx.lineTo(ox + childOffX + chbb.x / 2, oy + childOffY); // Draw a line to (150, 100)

              const chx = ox + childOffX + chbb.x / 2;
              // ctx.bezierCurveTo()
              ctx.arcTo(chx, paddedBottomY, chx, oy + childOffY, nodepady / 2);
              ctx.lineTo(chx, oy + childOffY);
              ctx.stroke(); // Render the path
              ctx.lineWidth = 1;

              childOffX += chbb.x;
            });
          };

          renderNode(rootNode, 0, 32);
          if (!didHighlightSomething) {
            if (hasActiveSpanHighlight) {
              hasActiveSpanHighlight = false;
              env.updateSpanHighlight(null);
            }
          }
          // ctx.fillStyle = '#FF0';
          // ctx.fillRect(lastClick.x - 32, lastClick.y - 32, 64, 64);

        };
        renderFrame();
        onResizePtr.callback = () => {
          renderFrame();
          // console.log('finished:', wrapper.clientWidth, wrapper.clientHeight, wrapper.offsetWidth);
        };

        // root.appendChild(document.createTextNode(JSON.stringify(state.data, null, 2)));
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
      adjusters.forEach(adj => adjustLocator(adj, locator));
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

  listTree(env, env.wrapTextRpc({
    query: {
      attr: {
        name: listDirection === 'upwards' ? 'meta:listTreeUpwards' : 'meta:listTreeDownwards',
      },
      locator,
    }
  }))
    .then((result) => {
      const refetch = fetchState == 'queued';
      fetchState = 'idle';
      if (refetch) fetchAttrs();

      const parsed = result.nodes;
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
      locator = result.locator;
      const mapNode = (src: ListedTreeNode): Node => ({
        type: src.type,
        locator: src.locator,
        name: src.name,
        children: Array.isArray(src.children)
          ? src.children.map(mapNode)
          : src.children,
      });
      state = { type: 'ok', data: mapNode(parsed) };
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
      locator,
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
