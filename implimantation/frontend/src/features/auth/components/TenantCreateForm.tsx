import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useRegisterTenantMutation } from '../api';
import { Shield, Plus, Loader2, CheckCircle2 } from 'lucide-react';
import { useState } from 'react';

const tenantSchema = z.object({
  name: z.string().min(3, 'Clinic name must be at least 3 characters'),
  slug: z.string()
    .min(3, 'Slug must be at least 3 characters')
    .regex(/^[a-z0-9-]+$/, 'Slug can only contain lowercase letters, numbers, and hyphens'),
  ownerEmail: z.string().email('Invalid email address'),
  ownerPassword: z.string().min(6, 'Password must be at least 6 characters'),
  ownerFirstName: z.string().min(1, 'First name is required'),
  ownerLastName: z.string().min(1, 'Last name is required'),
});

type TenantFormData = z.infer<typeof tenantSchema>;

export function TenantCreateForm() {
  const [registerTenant, { isLoading, error }] = useRegisterTenantMutation();
  const [success, setSuccess] = useState<string | null>(null);

  const { register, handleSubmit, setValue, formState: { errors } } = useForm<TenantFormData>({
    resolver: zodResolver(tenantSchema),
  });

  const onSubmit = async (data: TenantFormData) => {
    try {
      setSuccess(null);
      const res = await registerTenant(data).unwrap();
      setSuccess(`Tenant "${res.name}" registered successfully with slug: ${res.slug}`);
      setValue('name', '');
      setValue('slug', '');
      setValue('ownerEmail', '');
      setValue('ownerPassword', '');
      setValue('ownerFirstName', '');
      setValue('ownerLastName', '');
    } catch (err) {
      // Handled by RTK query state
    }
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const name = e.target.value;
    const generatedSlug = name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)+/g, '');
    setValue('slug', generatedSlug, { shouldValidate: true });
  };

  return (
    <div className="w-full max-w-lg p-8 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100/50">
      <div className="flex items-center mb-6 space-x-3">
        <div className="p-2.5 bg-teal-50 border border-teal-100 text-teal-600 rounded-xl">
          <Shield className="w-6 h-6" />
        </div>
        <div>
          <h3 className="text-xl font-bold text-slate-800">Create New Tenant</h3>
          <p className="text-slate-500 text-xs mt-0.5">Register a new isolated clinic domain</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Clinic Details Section */}
        <div className="space-y-4">
          <h4 className="text-xs font-bold uppercase tracking-wider text-teal-600 border-b border-teal-50 pb-1 mb-2">Clinic Details</h4>
          <div>
            <label htmlFor="name" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Clinic Name</label>
            <input
              {...register('name')}
              onChange={(e) => {
                register('name').onChange(e);
                handleNameChange(e);
              }}
              type="text"
              id="name"
              placeholder="Maple Health Clinic"
              className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
            />
            {errors.name && <p className="text-red-500 text-xs mt-1.5">{errors.name.message}</p>}
          </div>

          <div>
            <label htmlFor="slug" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Domain Slug</label>
            <div className="flex rounded-xl bg-slate-50 border border-slate-200 focus-within:ring-2 focus-within:ring-teal-500/30 focus-within:border-teal-500 transition-all">
              <span className="flex items-center pl-4 pr-1 text-slate-400 text-sm select-none">/</span>
              <input
                {...register('slug')}
                type="text"
                id="slug"
                placeholder="maple-health"
                className="block w-full py-2.5 pr-4 bg-transparent border-0 text-slate-800 placeholder-slate-400 focus:outline-none"
              />
            </div>
            <p className="text-[10px] text-slate-400 mt-1">This forms the unique login route: /maple-health/dashboard</p>
            {errors.slug && <p className="text-red-500 text-xs mt-1.5">{errors.slug.message}</p>}
          </div>
        </div>

        {/* Clinic Owner Account Section */}
        <div className="space-y-4 pt-2">
          <h4 className="text-xs font-bold uppercase tracking-wider text-teal-600 border-b border-teal-50 pb-1 mb-2">Clinic Owner Account</h4>
          
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="ownerFirstName" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">First Name</label>
              <input
                {...register('ownerFirstName')}
                type="text"
                id="ownerFirstName"
                placeholder="Jane"
                className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
              />
              {errors.ownerFirstName && <p className="text-red-500 text-xs mt-1.5">{errors.ownerFirstName.message}</p>}
            </div>
            <div>
              <label htmlFor="ownerLastName" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Last Name</label>
              <input
                {...register('ownerLastName')}
                type="text"
                id="ownerLastName"
                placeholder="Doe"
                className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
              />
              {errors.ownerLastName && <p className="text-red-500 text-xs mt-1.5">{errors.ownerLastName.message}</p>}
            </div>
          </div>

          <div>
            <label htmlFor="ownerEmail" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Email Address</label>
            <input
              {...register('ownerEmail')}
              type="email"
              id="ownerEmail"
              placeholder="owner@maple-health.com"
              className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
            />
            {errors.ownerEmail && <p className="text-red-500 text-xs mt-1.5">{errors.ownerEmail.message}</p>}
          </div>

          <div>
            <label htmlFor="ownerPassword" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Password</label>
            <input
              {...register('ownerPassword')}
              type="password"
              id="ownerPassword"
              placeholder="••••••••"
              className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
            />
            {errors.ownerPassword && <p className="text-red-500 text-xs mt-1.5">{errors.ownerPassword.message}</p>}
          </div>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-100 rounded-xl text-red-600 text-xs">
            <span>{(error as any).data?.message || 'Failed to create tenant.'}</span>
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
              Creating...
            </>
          ) : (
            <>
              <Plus className="w-5 h-5 mr-2" />
              Create Tenant
            </>
          )}
        </button>
      </form>
    </div>
  );
}
