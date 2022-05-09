import adjustTypeAtLoc from "./adjustTypeAtLoc";

const adjustLocator = (adj: LocationAdjuster, loc: NodeLocator) => {
  // adjustTypeAtLoc(adj, loc.root);
  adjustTypeAtLoc(adj, loc.result);

  const adjustStep = (step: NodeLocatorStep) => {
    switch (step.type) {
      case 'tal': {
        adjustTypeAtLoc(adj, step.value);
        break;
      }
      case 'nta': {
        step.value.args.forEach(({ args }) => {
          if (args) {
            args.forEach(({ value }) => {
              if (value && typeof value === 'object') {
                adjustLocator(adj, value);
              }
            })
          }
        });
        break;
      }
    }
  };
  loc.steps.forEach(adjustStep);

  // TODO when attributes are resolved, make sure to send refreshed locators back not only for root node, but also for the arg steps.
};

export default adjustLocator;
