
class UIElements {
  // Use lazy getters since the dom elements haven't been loaded
  // by the time this script initially runs.
  get positionRecoverySelector() { return document.getElementById('control-position-recovery-strategy') as HTMLSelectElement; }
  get positionRecoveryHelpButton() { return document.getElementById('control-position-recovery-strategy-help') as HTMLButtonElement; }

  get astCacheStrategySelector() { return document.getElementById('ast-cache-strategy') as HTMLSelectElement; }
  get astCacheStrategyHelpButton() { return document.getElementById('control-ast-cache-strategy-help') as HTMLButtonElement; }

  get syntaxHighlightingSelector() { return document.getElementById('syntax-highlighting') as HTMLSelectElement; }
  get syntaxHighlightingHelpButton() { return document.getElementById('control-syntax-highlighting-help') as HTMLButtonElement; }

  get shouldOverrideMainArgsCheckbox() { return document.getElementById('control-should-override-main-args') as HTMLInputElement; }
  get configureMainArgsOverrideButton() { return document.getElementById('configure-main-args') as HTMLButtonElement; }
  get mainArgsOverrideHelpButton() { return document.getElementById('main-args-override-help') as HTMLButtonElement; }

  get shouldCustomizeFileSuffixCheckbox() { return document.getElementById('control-customize-file-suffix') as HTMLInputElement; }
  get configureCustomFileSuffixButton() { return document.getElementById('customize-file-suffix') as HTMLButtonElement; }
  get customFileSuffixHelpButton() { return document.getElementById('customize-file-suffix-help') as HTMLButtonElement; }

  get showAllPropertiesCheckbox() { return document.getElementById('control-show-all-properties') as HTMLInputElement; }
  get showAllPropertiesHelpButton() { return document.getElementById('show-all-properties-help') as HTMLButtonElement; }

  get groupPropertiesByAspectCheckbox() { return document.getElementById('control-group-properties-by-aspect') as HTMLInputElement; }
  get groupPropertiesByAspectHelpButton() { return document.getElementById('group-properties-by-aspect-help') as HTMLButtonElement; }

  get autoShortenPropertyNamesCheckbox() { return document.getElementById('control-auto-shorten-property-names') as HTMLInputElement; }
  get autoShortenPropertyNamesHelpButton() { return document.getElementById('auto-shorten-property-names-help') as HTMLButtonElement; }

  get duplicateProbeCheckbox() { return document.getElementById('control-duplicate-probe-on-attr') as HTMLInputElement; }
  get duplicateProbeHelpButton() { return document.getElementById('duplicate-probe-on-attr-help') as HTMLButtonElement; }

  get captureStdoutCheckbox() { return document.getElementById('control-capture-stdout') as HTMLInputElement; }
  get captureStdoutHelpButton() { return document.getElementById('capture-stdout-help') as HTMLButtonElement; }

  get captureTracesCheckbox() { return document.getElementById('control-capture-traces') as HTMLInputElement; }
  get captureTracesHelpButton() { return document.getElementById('capture-traces-help') as HTMLButtonElement; }

  get autoflushTracesCheckbox() { return document.getElementById('control-autoflush-traces') as HTMLInputElement; }
  get autoflushTracesContainer() { return document.getElementById('container-autoflush-traces') as HTMLDivElement; }

  get locationStyleSelector() { return document.getElementById('location-style') as HTMLSelectElement; }
  get locationStyleHelpButton() { return document.getElementById('control-location-style-help') as HTMLButtonElement; }

  get textprobeStyleSelector() { return document.getElementById('textprobe-style') as HTMLSelectElement; }
  get textprobeStyleHelpButton() { return document.getElementById('control-textprobe-style-help') as HTMLButtonElement; }

  get workspaceHeaderLabel() { return document.getElementById('workspace-header') as HTMLElement; }
  get workspaceListWrapper() { return document.getElementById('workspace-wrapper') as HTMLElement; }
  get workspaceFindFile() { return document.getElementById('workspace-find-file') as HTMLButtonElement; }
  get workspaceTestRunner() { return document.getElementById('workspace-test-runner') as HTMLButtonElement; }

  get generalHelpButton() { return document.getElementById('display-help') as HTMLButtonElement; }
  get saveAsUrlButton() { return document.getElementById('saveAsUrl') as HTMLButtonElement; }
  get themeDropdown() { return document.getElementById('control-theme') as HTMLSelectElement; }
  get displayStatisticsButton() { return document.getElementById('display-statistics') as HTMLButtonElement; }
  get displayWorkerStatusButton() { return document.getElementById('display-worker-status') as HTMLButtonElement; }
  get versionInfo() { return document.getElementById('version') as HTMLDivElement; }
  get settingsHider() { return document.getElementById('settings-hider') as HTMLButtonElement; }
  get settingsRevealer() { return document.getElementById('settings-revealer') as HTMLButtonElement; }
  get showTests() { return document.getElementById('show-tests') as HTMLButtonElement; }
  get minimizedProbeArea() { return document.getElementById('minimized-probe-area') as HTMLDivElement; }
}

export default UIElements;
