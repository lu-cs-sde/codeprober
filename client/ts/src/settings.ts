

interface Settings {
  editorContents?: string;
  lightTheme?: boolean;
  captureStdio?: boolean;
  duplicateProbeOnAttrClick?: boolean;
  positionRecoveryStrategy?: string;
  astCacheStrategy?: string;
  probeWindowStates?: ProbeWindowState[];
}

let settingsObj: Settings | null = null;

const settings = {
  get: (): Settings => {
    if (!settingsObj) {
      try {
        settingsObj = JSON.parse(localStorage.getItem('pasta-settings') || '{}');
      } catch (e) {
        console.warn('Bad data in localStorage, resetting settings', e);
        settingsObj = {};
      }
    }
    return settingsObj || {};
  },
  set: (newSettings: Settings) => {
    settingsObj = newSettings;
    localStorage.setItem('pasta-settings', JSON.stringify(settingsObj));
  },

  getEditorContents: () => settings.get().editorContents,
  setEditorContents: (editorContents: string) => settings.set({ ...settings.get(), editorContents }),

  isLightTheme: () => settings.get().lightTheme ?? false,
  setLightTheme: (lightTheme: boolean) => settings.set({ ...settings.get(), lightTheme }),

  shouldDuplicateProbeOnAttrClick: () => settings.get().duplicateProbeOnAttrClick ?? true,
  setShouldDuplicateProbeOnAttrClick: (duplicateProbeOnAttrClick: boolean) => settings.set({ ...settings.get(), duplicateProbeOnAttrClick }),
  shouldCaptureStdio: () => settings.get().captureStdio ?? true,
  setShouldCaptureStdio: (captureStdio: boolean) => settings.set({ ...settings.get(), captureStdio }),

  getPositionRecoveryStrategy: () => settings.get().positionRecoveryStrategy ?? 'ALTERNATE_PARENT_CHILD',
  setPositionRecoveryStrategy: (positionRecoveryStrategy: string) => settings.set({ ...settings.get(), positionRecoveryStrategy }),

  getAstCacheStrategy: () => settings.get().astCacheStrategy ?? 'PARTIAL',
  setAstCacheStrategy: (astCacheStrategy: string) => settings.set({ ...settings.get(), astCacheStrategy }),

  getProbeWindowStates: () => settings.get().probeWindowStates ?? [],
  setProbeWindowStates: (probeWindowStates: ProbeWindowState[]) => settings.set({ ...settings.get(), probeWindowStates }),
};

export default settings;
