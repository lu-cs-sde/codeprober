
class UIElements {
  // Use lazy getters since the dom elements haven't been loaded
  // by the time this script initially runs.
  get positionRecoverySelector() { return document.getElementById('control-position-recovery-strategy') as HTMLSelectElement; }
  get positionRecoveryHelpButton() { return document.getElementById('control-position-recovery-strategy-help') as HTMLButtonElement; }

  get astCacheStrategySelector() { return document.getElementById('ast-cache-strategy') as HTMLSelectElement; }
  get astCacheStrategyHelpButton() { return document.getElementById('control-ast-cache-strategy-help') as HTMLButtonElement; }

  get syntaxHighlightingSelector() { return document.getElementById('syntax-highlighting') as HTMLSelectElement; }
  get syntaxHighlightingHelpButton() { return document.getElementById('control-syntax-highlighting-help') as HTMLButtonElement; }

  get shouldOverrideMainArgsCheckbox() { return document.getElementById('control-should-override-main-args') as HTMLInputElement; }
  get configureMainArgsOverrideButton() { return document.getElementById('configure-main-args') as HTMLButtonElement; }
  get mainArgsOverrideHelpButton() { return document.getElementById('main-args-override-help') as HTMLButtonElement; }

  get shouldCustomizeFileSuffixCheckbox() { return document.getElementById('control-customize-file-suffix') as HTMLInputElement; }
  get configureCustomFileSuffixButton() { return document.getElementById('customize-file-suffix') as HTMLButtonElement; }
  get customFileSuffixHelpButton() { return document.getElementById('customize-file-suffix-help') as HTMLButtonElement; }

  get showAllPropertiesCheckbox() { return document.getElementById('control-show-all-properties') as HTMLInputElement; }
  get showAllPropertiesHelpButton() { return document.getElementById('show-all-properties-help') as HTMLButtonElement; }

  get duplicateProbeCheckbox() { return document.getElementById('control-duplicate-probe-on-attr') as HTMLInputElement; }
  get duplicateProbeHelpButton() { return document.getElementById('duplicate-probe-on-attr-help') as HTMLButtonElement; }

  get captureStdoutCheckbox() { return document.getElementById('control-capture-stdout') as HTMLInputElement; }
  get captureStdoutHelpButton() { return document.getElementById('capture-stdout-help') as HTMLButtonElement; }

  get locationStyleSelector() { return document.getElementById('location-style') as HTMLSelectElement; }
  get locationStyleHelpButton() { return document.getElementById('control-location-style-help') as HTMLButtonElement; }

  get generalHelpButton() { return document.getElementById('display-help') as HTMLButtonElement; }
  get darkModeCheckbox() { return document.getElementById('control-dark-mode') as HTMLInputElement; }
  get displayStatisticsButton() { return document.getElementById('display-statistics') as HTMLButtonElement; }
  get versionInfo() { return document.getElementById('version') as HTMLDivElement; }
}

export default UIElements;
