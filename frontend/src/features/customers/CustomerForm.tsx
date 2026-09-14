import {
    useState,
    type FormEvent,
} from "react";

import type {
    Customer,
    CreateCustomerRequest,
} from "./customerTypes";

interface CustomerFormProps {
    customer?: Customer;

    onSubmit:
        (request: CreateCustomerRequest) =>
            Promise<void>;

    onCancel: () => void;
}

export default function CustomerForm({
    customer,
    onSubmit,
    onCancel,
}: CustomerFormProps) {
    const [firstName, setFirstName] =
        useState(customer?.firstName ?? "");

    const [lastName, setLastName] =
        useState(customer?.lastName ?? "");

    const [email, setEmail] =
        useState(customer?.email ?? "");

    const [phone, setPhone] =
        useState(customer?.phone ?? "");

    const [submitting, setSubmitting] =
        useState(false);

    async function handleSubmit(
        event: FormEvent<HTMLFormElement>
    ) {
        event.preventDefault();

        setSubmitting(true);

        try {
            await onSubmit({
                firstName: firstName.trim(),
                lastName: lastName.trim(),

                email:
                    email.trim() === ""
                        ? null
                        : email.trim(),

                phone:
                    phone.trim() === ""
                        ? null
                        : phone.trim(),
            });
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <form onSubmit={handleSubmit}>
            <div>
                <label>
                    First name
                </label>

                <input
                    value={firstName}
                    onChange={(event) =>
                        setFirstName(
                            event.target.value
                        )
                    }
                    required
                />
            </div>

            <div>
                <label>
                    Last name
                </label>

                <input
                    value={lastName}
                    onChange={(event) =>
                        setLastName(
                            event.target.value
                        )
                    }
                    required
                />
            </div>

            <div>
                <label>
                    Email
                </label>

                <input
                    type="email"
                    value={email}
                    onChange={(event) =>
                        setEmail(
                            event.target.value
                        )
                    }
                />
            </div>

            <div>
                <label>
                    Phone
                </label>

                <input
                    value={phone}
                    onChange={(event) =>
                        setPhone(
                            event.target.value
                        )
                    }
                />
            </div>

            <button
                type="button"
                onClick={onCancel}
            >
                Cancel
            </button>

            <button
                type="submit"
                disabled={submitting}
            >
                {submitting
                    ? "Saving..."
                    : customer
                        ? "Save changes"
                        : "Create customer"}
            </button>
        </form>
    );
}