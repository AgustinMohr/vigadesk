import { ApiError } from "./api-error";

export class ApiClient {

    async get<T>(url: string): Promise<T> {

        throw new Error("Not implemented");

    }

    async post<T>(
        url: string,
        body: unknown
    ): Promise<T> {

        throw new Error("Not implemented");

    }

    async put<T>(
        url: string,
        body: unknown
    ): Promise<T> {

        throw new Error("Not implemented");

    }

    async delete(
        url: string
    ): Promise<void> {

        throw new Error("Not implemented");

    }

}