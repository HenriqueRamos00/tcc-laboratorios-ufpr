const IEC_60599_TYPICAL_VALUES: Readonly<Record<string, number>> = {
  h2: 50,
  ch4: 30,
  c2h6: 20,
  c2h4: 60,
  c2h2: 2,
};

export function belowTypicalValues(concentrations: Readonly<Record<string, number | null>>): boolean {
  return Object.entries(IEC_60599_TYPICAL_VALUES).every(([gas, typical]) => {
    const measured = concentrations[gas] ?? null;
    return measured !== null && measured <= typical;
  });
}
