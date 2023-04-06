

class StatisticsCollectorImpl implements ProbeStatisticsCollector {

  private lastMeasurement: ProbeMeasurement | null;
  private mergedMeasurements: ProbeMeasurement;
  private numberOfMeasurements: number;
  private onChange: (() => void) | null;
  constructor(
  ) {
    this.lastMeasurement = null;
    this.mergedMeasurements = { fullRpcMs: 0, serverSideMs: 0, serverParseOnlyMs: 0, serverCreateLocatorMs: 0, serverApplyLocatorMs: 0, attrEvalMs: 0 };
    this.numberOfMeasurements = 0;
    this.onChange = null;
  };

  addProbeEvaluationTime(measure: ProbeMeasurement) {
    this.lastMeasurement = measure;
    this.mergedMeasurements.fullRpcMs += measure.fullRpcMs;
    this.mergedMeasurements.serverSideMs += measure.serverSideMs;
    this.mergedMeasurements.serverParseOnlyMs += measure.serverParseOnlyMs;
    this.mergedMeasurements.serverCreateLocatorMs += measure.serverCreateLocatorMs;
    this.mergedMeasurements.serverApplyLocatorMs += measure.serverApplyLocatorMs;
    this.mergedMeasurements.attrEvalMs += measure.attrEvalMs;
    this.numberOfMeasurements++;
    this.onChange?.();
  }

  setOnChange(callback: (() => void) | null) {
    this.onChange = callback;
  }

  getLastMeasurement() { return this.lastMeasurement }
  getMergedMeasurements() { return this.mergedMeasurements }
  getNumberOfMeasurements() { return this.numberOfMeasurements }

  reset() {
    this.lastMeasurement = null;
    this.mergedMeasurements = { fullRpcMs: 0, serverSideMs: 0, serverParseOnlyMs: 0, serverCreateLocatorMs: 0, serverApplyLocatorMs: 0, attrEvalMs: 0 };
    this.numberOfMeasurements = 0;
    this.onChange?.();
  }
}

export default StatisticsCollectorImpl;
