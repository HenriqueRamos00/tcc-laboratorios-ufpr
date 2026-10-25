export function separarEmailParaQuebra(email: string): string[] {
  return email.split(/(?<=[@._-])/u);
}
