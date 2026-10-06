export function modelSelectionValue(configId: number, modelName: string): string {
  return `${configId}:${encodeURIComponent(modelName)}`
}

export function parseModelSelection(value: string): { configId: number; modelName: string } {
  const separator = value.indexOf(':')
  return {
    configId: Number(value.slice(0, separator)),
    modelName: decodeURIComponent(value.slice(separator + 1)),
  }
}
