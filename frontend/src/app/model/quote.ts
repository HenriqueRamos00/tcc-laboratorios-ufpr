// Contrato de orçamentos retornado pela API C# (camelCase).
// GET /api/quotes e GET /api/quotes/{id}
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
