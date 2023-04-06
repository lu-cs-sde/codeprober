import StatisticsCollectorImpl from "../../model/StatisticsCollectorImpl";
import createModalTitle from "../create/createModalTitle";
import showWindow from "../create/showWindow";

interface StatLabelGroup {
  serverTotalLbl: HTMLElement;
  parseLbl: HTMLElement;
  rpcLbl: HTMLElement;
  createLocatorLbl: HTMLElement;
  applyLocatorLbl: HTMLElement;
  attrEvalLbl: HTMLElement;
};
interface StatLabels {
  numEvaluations: HTMLElement;
  average: StatLabelGroup;
  mostRecent: StatLabelGroup;
}

const displayStatistics = (collector: StatisticsCollectorImpl, setStatisticsButtonDisabled: (disabled: boolean) => void, setEditorContentsAndUpdateProbes: (newContents: string) => void, anyModalIsLoading: () => boolean): void => {
  let simulateTimer: number | any = -1;
  const onClose = () => {
    collector.setOnChange(null);
    helpWindow.remove();
    setStatisticsButtonDisabled(false);
    clearInterval(simulateTimer);
  };
  setStatisticsButtonDisabled(true);

  let statLabels: StatLabels | null = null;
  let controlButtons: HTMLDivElement | null = null;

  const generateMethodGenerator = (cycles: number): (() => string) => {
    return () => {
      const alreadyGeneratedIds = new Set();
      const genId = (prefix: string) => {
        let newId;
        do {
          newId = `${Math.max(10_000, Math.floor(Math.random() * 1_000_000))}`;
        } while (alreadyGeneratedIds.has(newId));
        alreadyGeneratedIds.add(newId);
        return `${prefix}${newId}`;
      };
      const pickRandom = (...options: string[]) => options[Math.floor(Math.random() * options.length)];
      return [
        // ...[...Array(cycles)].map(() => `${['interface', 'abstract class', 'enum'][Math.floor(Math.random() * 3)]} ${genId('Other')} { /* Empty */ }`),
        `class ${genId('Benchmark')} {`,
        ...[...Array(cycles)].map(() => [
          `  ${pickRandom('interface', 'abstract class', 'enum')} ${genId('Other')} { /* Empty */ }`,
          `  static void ${genId('f')}(String[] ${genId('arg')}, ${Math.random() > 0.5 ? 'int' : 'byte'} ${genId('arg')}) {`,
          `    final long local = System.currentTimeMillis() % ${genId('')}L;`,
          `    if (local ${pickRandom('<', '>', '==', '!=', '>=', '<=')} ${genId('')}L) { System.out.println(local); }`,
          `    else { System.out.println(${genId('')}); }`,
          `  }`,
        ].join('\n')),
        '}',
      ].join('\n');
    };
  }
  const tests: { title: string, contents: () => string }[] = [
    {
      title: 'Java - Tiny',
      contents: generateMethodGenerator(1),
    },
    {
      title: 'Java - Medium',
      contents: generateMethodGenerator(5)
    },
    {
      title: 'Java - Large',
      contents: generateMethodGenerator(50),
    },
    {
      title: 'Java - Enormous',
      contents: generateMethodGenerator(500),
    },
  ];
  let activeTest = tests[0].title;

  const helpWindow = showWindow({
    rootStyle: `
      width: 32rem;
      min-height: 12rem;
    `,
    onForceClose: onClose,
    resizable: true,
    render: (root) => {

      const merged = collector.getMergedMeasurements();
      const numMeasurement = collector.getNumberOfMeasurements();

      interface ComputedValue {
        serverTotalMs: string;
        parseMs: string;
        rpcMs: string;
        createLocatorMs: string;
        applyLocatorMs: string;
        attributeEvalMs: string;
      }

      const formatNumber = (num: number) => num.toFixed(1);
      const computeAverage = (): ComputedValue => {
        const computeOfTotal = (num: number) => `${formatNumber(num * 100 / merged.serverSideMs)}%`;
        const serverTotalMs = `${formatNumber(merged.serverSideMs / numMeasurement)}ms`;
        const parseMs = `${formatNumber(merged.serverParseOnlyMs / numMeasurement)}ms (${computeOfTotal(merged.serverParseOnlyMs)})`;
        const rpcMs = `${formatNumber((merged.fullRpcMs - merged.serverSideMs) / numMeasurement)}ms`;
        const createLocatorMs = `${formatNumber(merged.serverCreateLocatorMs / numMeasurement)}ms (${computeOfTotal(merged.serverCreateLocatorMs)})`;
        const applyLocatorMs = `${formatNumber(merged.serverApplyLocatorMs / numMeasurement)}ms (${computeOfTotal(merged.serverApplyLocatorMs)})`;
        const attributeEvalMs = `${formatNumber(merged.attrEvalMs / numMeasurement)}ms (${computeOfTotal(merged.attrEvalMs)})`;

        return { serverTotalMs, parseMs, rpcMs, createLocatorMs, applyLocatorMs, attributeEvalMs };
      }
      const computeIndividual = (last: ProbeMeasurement): ComputedValue => {
        const computeOfTotal = (num: number) => `${formatNumber(num * 100 / last.serverSideMs)}%`;
          const serverTotalMs = `${formatNumber(last.serverSideMs)}ms`;
          const parseMs = `${formatNumber(last.serverParseOnlyMs)}ms (${computeOfTotal(last.serverParseOnlyMs)})`;
          const rpcMs = `${formatNumber(last.fullRpcMs - last.serverSideMs)}ms`;
          const createLocatorMs = `${formatNumber(last.serverCreateLocatorMs)}ms (${computeOfTotal(last.serverCreateLocatorMs)})`;
          const applyLocatorMs = `${formatNumber(last.serverApplyLocatorMs)}ms (${computeOfTotal(last.serverApplyLocatorMs)})`;
          const attributeEvalMs = `${formatNumber(last.attrEvalMs)}ms (${computeOfTotal(last.attrEvalMs)})`;

          return { serverTotalMs, parseMs, rpcMs, createLocatorMs, applyLocatorMs, attributeEvalMs };
      }

      const last = collector.getLastMeasurement();
      if (!statLabels || !last) {
        statLabels = null;
        while (root.firstChild) root.firstChild.remove();

        root.appendChild(createModalTitle({
          renderLeft: (container) => {
            const header = document.createElement('span');
            header.innerText = 'Statistics';
            container.appendChild(header);
          },
          onClose,
        }).element);

        const grid = document.createElement('div');
        grid.style.display = 'grid';
        grid.style.padding = '0.25rem';
        // grid.style.justifyItems = 'center';
        grid.style.gridGap = '4px';
        grid.style.gridTemplateColumns = 'auto 1fr';

        const addRow = (title: string, measurement: string, boldTitle = false) => {
          const titleNode = document.createElement('span');
          titleNode.innerText = title;
          titleNode.style.textAlign = 'right';
          titleNode.style.textAlign = 'right';
          if (boldTitle) {
            titleNode.style.fontWeight = 'bold';
          }

          const measurementNode = document.createElement('span');
          measurementNode.innerText = measurement;

          grid.appendChild(titleNode);
          grid.appendChild(measurementNode);
          return measurementNode;
        }

        const addDivider = () => {
          const divider = document.createElement('div');
          divider.style.borderTop = '1px solid gray';
          grid.appendChild(divider);
          grid.appendChild(divider.cloneNode(true));
        }


        const numEvalsLbl = addRow('Num evaluations', `${numMeasurement}`);
        addRow('', '');

        const addGroup = (measurements: ComputedValue): StatLabelGroup => {
          const serverTotalLbl = addRow('Server side total', measurements.serverTotalMs);
          const parseLbl = addRow('..of which AST parse', measurements.parseMs);
          const createLocatorLbl = addRow('..of which locator create', measurements.createLocatorMs);
          const applyLocatorLbl = addRow('..of which locator apply', measurements.applyLocatorMs);
          const attrEvalLbl = addRow('..of which attribute eval', measurements.attributeEvalMs);
          addRow('', '');

          const rpcLbl = addRow('RPC overhead:', measurements.rpcMs)
          addDivider();

          return { serverTotalLbl, parseLbl, rpcLbl, createLocatorLbl, applyLocatorLbl, attrEvalLbl };
        };
        let averageLabels: StatLabelGroup | null = null;
        if (numMeasurement > 0) {
          addRow('Average', '', true);

          averageLabels = addGroup(computeAverage());
        }

        let mostRecentLabels: StatLabelGroup | null = null;
        if (last) {
          addRow('Most recent', '', true);
          mostRecentLabels = addGroup(computeIndividual(last));
        }

        root.appendChild(grid);

        if (controlButtons) {
          root.appendChild(controlButtons);
        } else {
          controlButtons = document.createElement('div');
          controlButtons.style.padding = '0.5rem';
          root.appendChild(controlButtons);

          const addButton = (title: string, onClick: (btn: HTMLButtonElement) => void) => {
            const btn = document.createElement('button');
            btn.innerText = title;
            btn.onclick = () => onClick(btn);
            btn.style.marginRight = '0.25rem';
            controlButtons!.appendChild(btn);
            return btn;
          }
          const stopSimulation = () => {
            clearInterval(simulateTimer);
            simulateTimer = -1;
            resetBtn.innerText = 'Reset';
            measurementBtn.disabled = false;
          }
          let resetBtn = addButton('Reset', () => {
            if (simulateTimer !== -1) {
              stopSimulation();
            } else {
              collector.reset();
            }
          });
          const measurementBtn = addButton('Run benchmark', (btn) => {
            btn.disabled = true;
            collector.reset();
            clearInterval(simulateTimer);
            resetBtn.innerText = 'Stop';

            // let expectMeasurements = 0;

            const triggerChange = () => {
              setEditorContentsAndUpdateProbes(tests.find(({ title }) => title === activeTest)!.contents());
            };
            let prevChangeCounter = collector.getNumberOfMeasurements();
            triggerChange();
            simulateTimer = setInterval(() => {
              const newChangeCounter = collector.getNumberOfMeasurements();
              if (anyModalIsLoading() || newChangeCounter == prevChangeCounter) {
                return;
              }
              prevChangeCounter = newChangeCounter;
              if (newChangeCounter >= 10_000) {
                stopSimulation();
              } else {
                triggerChange();
              }
            }, 30);
          });
        }

        root.appendChild(document.createElement('hr'));

        const testSuiteHolder = document.createElement('div');
        testSuiteHolder.style.padding = '0.25rem';

        const testSuiteSelector = document.createElement('select');
        testSuiteSelector.style.marginRight = '0.5rem';
        testSuiteSelector.id = 'test-type-selector';

        tests.forEach(({ title }) => {
          const option = document.createElement('option');
          option.value = title;
          option.innerText = title;
          testSuiteSelector.appendChild(option);
        })
        testSuiteSelector.selectedIndex = tests.findIndex(({ title }) => title === activeTest);
        testSuiteHolder.appendChild(testSuiteSelector);
        testSuiteSelector.oninput = () => {
          activeTest = testSuiteSelector.value;
        }

        const testSuiteLabel = document.createElement('label');
        testSuiteLabel.innerText = 'Benchmark type';
        testSuiteHolder.setAttribute('for', 'test-type-selector');
        testSuiteHolder.appendChild(testSuiteLabel);


        root.appendChild(testSuiteHolder);

        if (averageLabels && mostRecentLabels) {

          statLabels = {
            numEvaluations: numEvalsLbl,
            average: averageLabels,
            mostRecent: mostRecentLabels,
          }
        }
      } else {
        const apply = (group: StatLabelGroup, val: ComputedValue) => {
          group.serverTotalLbl.innerText = val.serverTotalMs;
          group.createLocatorLbl.innerText = val.createLocatorMs;
          group.applyLocatorLbl.innerText = val.applyLocatorMs;
          group.parseLbl.innerText = val.parseMs;
          group.rpcLbl.innerText = val.rpcMs;
          group.attrEvalLbl.innerText = val.attributeEvalMs;
        }
        statLabels.numEvaluations.innerText = `${collector.getNumberOfMeasurements()}`;
        apply(statLabels.average, computeAverage());
        apply(statLabels.mostRecent, computeIndividual(last));
      }
    },
  });
  collector.setOnChange(() => helpWindow.refresh());
}

export default displayStatistics;
