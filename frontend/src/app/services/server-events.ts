import {
  HttpClient,
  HttpDownloadProgressEvent,
  HttpEvent,
  HttpEventType,
  HttpHeaders,
} from '@angular/common/http';
import { EMPTY, from, mergeMap, Observable } from 'rxjs';

export interface ServerEvent {
  readonly id: string | null;
  readonly name: string;
  readonly data: string;
}

const DEFAULT_EVENT_NAME = 'message';

export class ServerEventReader {
  private rest = '';

  read(chunk: string): ServerEvent[] {
    const text = (this.rest + chunk).replace(/\r\n?/g, '\n');
    const blocks = text.split('\n\n');

    this.rest = blocks.pop() ?? '';

    return blocks.map(toEvent).filter((event): event is ServerEvent => event !== null);
  }
}

function toEvent(block: string): ServerEvent | null {
  let id: string | null = null;
  let name: string | null = null;
  const data: string[] = [];

  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue;

    const separator = line.indexOf(':');
    const field = separator === -1 ? line : line.slice(0, separator);
    const value = separator === -1 ? '' : line.slice(separator + 1).replace(/^ /, '');

    if (field === 'id') id = value;
    else if (field === 'event') name = value;
    else if (field === 'data') data.push(value);
  }

  if (name === null && data.length === 0) return null;

  return { id, name: name ?? DEFAULT_EVENT_NAME, data: data.join('\n') };
}

export function serverEvents(
  http: HttpClient,
  url: string,
  lastEventId: string | null = null,
): Observable<ServerEvent> {
  const reader = new ServerEventReader();
  let alreadyRead = 0;
  let headers = new HttpHeaders({ Accept: 'text/event-stream' });

  if (lastEventId) headers = headers.set('Last-Event-ID', lastEventId);

  return http
    .get(url, { headers, responseType: 'text', observe: 'events', reportProgress: true })
    .pipe(
      mergeMap((event) => {
        const text = receivedText(event);

        if (text === null) return EMPTY;

        const fresh = text.slice(alreadyRead);
        alreadyRead = text.length;

        return from(reader.read(fresh));
      }),
    );
}

function receivedText(event: HttpEvent<string>): string | null {
  if (event.type === HttpEventType.DownloadProgress) {
    return (event as HttpDownloadProgressEvent).partialText ?? null;
  }

  if (event.type === HttpEventType.Response) return event.body ?? '';

  return null;
}
