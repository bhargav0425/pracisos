import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useInviteUserMutation } from '../api';
import { Plus, Loader2, CheckCircle2, UserPlus } from 'lucide-react';
import { useState } from 'react';

const inviteSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  role: z.enum(['PRACTITIONER', 'RECEPTIONIST']),
});

type InviteFormData = z.infer<typeof inviteSchema>;

export function UserInviteForm() {
  const [inviteUser, { isLoading, error }] = useInviteUserMutation();
  const [success, setSuccess] = useState<string | null>(null);

  const { register, handleSubmit, setValue, formState: { errors } } = useForm<InviteFormData>({
    resolver: zodResolver(inviteSchema),
    defaultValues: {
      role: 'PRACTITIONER',
    }
  });

  const onSubmit = async (data: InviteFormData) => {
    try {
      setSuccess(null);
      const res = await inviteUser(data).unwrap();
      setSuccess(`Successfully invited ${res.fullName} as ${res.role}!`);
      setValue('email', '');
      setValue('firstName', '');
      setValue('lastName', '');
    } catch (err) {
      // Handled by RTK query state
    }
  };

  return (
    <div className="w-full max-w-lg p-8 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100/50">
      <div className="flex items-center mb-6 space-x-3">
        <div className="p-2.5 bg-teal-50 border border-teal-100 text-teal-600 rounded-xl">
          <UserPlus className="w-6 h-6" />
        </div>
        <div>
          <h3 className="text-xl font-bold text-slate-800">Invite Clinic Staff</h3>
          <p className="text-slate-500 text-xs mt-0.5">Invite new practitioners or receptionists to join</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="firstName" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">First Name</label>
            <input
              {...register('firstName')}
              type="text"
              id="firstName"
              placeholder="John"
              className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
            />
            {errors.firstName && <p className="text-red-500 text-xs mt-1.5">{errors.firstName.message}</p>}
          </div>

          <div>
            <label htmlFor="lastName" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Last Name</label>
            <input
              {...register('lastName')}
              type="text"
              id="lastName"
              placeholder="Doe"
              className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
            />
            {errors.lastName && <p className="text-red-500 text-xs mt-1.5">{errors.lastName.message}</p>}
          </div>
        </div>

        <div>
          <label htmlFor="email" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Email Address</label>
          <input
            {...register('email')}
            type="email"
            id="email"
            placeholder="staff@clinic.com"
            className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
          />
          {errors.email && <p className="text-red-500 text-xs mt-1.5">{errors.email.message}</p>}
        </div>

        <div>
          <label htmlFor="role" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Staff Role</label>
          <select
            {...register('role')}
            id="role"
            className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
          >
            <option value="PRACTITIONER" className="bg-white text-slate-800">Practitioner (Healthcare Provider)</option>
            <option value="RECEPTIONIST" className="bg-white text-slate-800">Receptionist (Front-desk)</option>
          </select>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-100 rounded-xl text-red-600 text-xs">
            <span>{(error as any).data?.message || 'Failed to send invitation.'}</span>
          </div>
        )}

        {success && (
          <div className="p-3.5 bg-emerald-50 border border-emerald-100 rounded-xl flex items-start text-emerald-600 text-xs">
            <CheckCircle2 className="w-4 h-4 mr-2.5 shrink-0" />
            <span>{success}</span>
          </div>
        )}

        <button
          type="submit"
          disabled={isLoading}
          className="w-full flex items-center justify-center py-2.5 px-4 bg-teal-600 hover:bg-teal-700 text-white font-semibold rounded-xl transition-all shadow-md shadow-teal-500/10 active:scale-[0.98] disabled:opacity-50"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-5 h-5 mr-2 animate-spin" />
              Inviting...
            </>
          ) : (
            <>
              <Plus className="w-5 h-5 mr-2" />
              Invite Staff
            </>
          )}
        </button>
      </form>
    </div>
  );
}
