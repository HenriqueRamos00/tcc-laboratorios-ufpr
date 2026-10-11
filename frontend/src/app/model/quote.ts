// Contrato de orçamentos retornado pela API Spring (camelCase).
// GET /api/client/quotes e GET /api/client/quotes/{id}
export type Quote = {
  id: string;
  code: string;
  name: string;
  status: string;
  stage: string;
  description: string;
  companyName: string;
  externalContactName: string;
  externalContactEmail: string;
  totalPrice: number;
  createdDate: string | null;
};
