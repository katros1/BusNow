export interface Driver {
  id: string;
  firstName: string;
  lastName: string;
  gender: "MALE" | "FEMALE";
  phoneNumber: string;
  licenseNumber: string;
  licenseCategory: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDriverPayload {
  firstName: string;
  lastName: string;
  gender: "MALE" | "FEMALE";
  phoneNumber: string;
  licenseNumber: string;
  licenseCategory: string;
}

export interface UpdateDriverPayload extends Partial<CreateDriverPayload> {}
