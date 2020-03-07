package com.wzq.mvvmsmart.base;


import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProviders;

import com.afollestad.materialdialogs.MaterialDialog;
import com.wzq.mvvmsmart.base.BaseViewModelMVVM.ParameterField;
import com.wzq.mvvmsmart.utils.MaterialDialogUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

public abstract class BaseFragmentMVVM<V extends ViewDataBinding, VM extends BaseViewModelMVVM> extends Fragment implements IBaseViewMVVM {
    protected V binding;
    protected VM viewModel;
    private int viewModelId;
    private MaterialDialog dialog;
    private boolean isNavigationViewInit = false; // 记录是否已经初始化过一次视图
    private View lastView = null; // 记录上次创建的view

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initParam();
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //如果fragment的view已经创建则不再重新创建
        if (lastView == null) {
            binding = DataBindingUtil.inflate(inflater, initContentView(inflater, container, savedInstanceState), container, false);
            binding.setLifecycleOwner(this);
            lastView = binding.getRoot();
        }
        return lastView;
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        if (!isNavigationViewInit) {//初始化过视图则不再进行view和data初始化
            super.onViewCreated(view, savedInstanceState);
            //私有的初始化Databinding和ViewModel方法
            initViewDataBinding();
            //私有的ViewModel与View的契约事件回调逻辑
            registorUIChangeLiveDataCallBack();
            //页面数据初始化方法
            initData();
            initToolbar();
            //页面事件监听的方法，一般用于ViewModel层转到View层的事件注册
            initViewObservable();
        }
        isNavigationViewInit = true;
    }

    /**
     * 注入绑定
     */
    @SuppressWarnings("unchecked")
    private void initViewDataBinding() {
        viewModelId = initVariableId();
        viewModel = initViewModel();
        if (viewModel == null) {
            Class modelClass;
            //返回表示此 Class 所表示的实体（类、接口、基本类型或 void）的直接超类的 Type
            //然后将其转换ParameterizedType
            Type type = getClass().getGenericSuperclass();
            if (type instanceof ParameterizedType) {
                // 返回表示此类型实际类型参数的 Type 对象的数组。简而言之就是获得超类的泛型参数的实际类型,获取第二个泛型
                modelClass = (Class) ((ParameterizedType) type).getActualTypeArguments()[1];
            } else {
                //如果没有指定泛型参数，则默认使用BaseViewModel
                modelClass = BaseViewModelMVVM.class;
            }
            viewModel = (VM) createViewModel(this, modelClass);
        }
        binding.setVariable(viewModelId, viewModel);
        /*
         * 让ViewModel拥有View的生命周期感应
         * viewModel implements IBaseViewModel接口
         * IBaseViewModelMVVM extends LifecycleObserver
         * 所以ViewModel是lifecycle生命周期的观察者,viewmode可以在不同的生命周期中处理不同的事情
         * viewModel可以感受到ui的生命周期状态;
         * BaseViewModel中实现了IBaseViewModel中的类似生命周期的观察
         */
        getLifecycle().addObserver(viewModel);
    }

    /**
     * @param cls 类
     * @param <T> 泛型参数,必须继承ViewMode
     * @return 生成的viewMode实例
     */
    private <T extends ViewModel> T createViewModel(Fragment fragment, Class<T> cls) {
        return ViewModelProviders.of(fragment).get(cls);
    }

    /**
     * =====================================================================
     **/
    //注册ViewModel与View的契约UI回调事件
    protected void registorUIChangeLiveDataCallBack() {
        //跳入新页面
        viewModel.getUC().getStartActivityLiveData().observe(this, new Observer<Map<String, Object>>() {
            @Override
            public void onChanged(@Nullable Map<String, Object> params) {
                Class<?> clz = (Class<?>) params.get(ParameterField.CLASS);
                Bundle bundle = (Bundle) params.get(ParameterField.BUNDLE);
                startActivity(clz, bundle);
            }
        });

        //关闭界面
        viewModel.getUC().getFinishLiveData().observe(this, new Observer<Void>() {
            @Override
            public void onChanged(@Nullable Void v) {
                getActivity().finish();
            }
        });

        //关闭上一层
        viewModel.getUC().getOnBackPressedLiveData().observe(this, new Observer<Void>() {
            @Override
            public void onChanged(@Nullable Void v) {
                getActivity().onBackPressed();
            }
        });
    }


    public void showLoading(String title) {
        if (dialog != null) {
            dialog = dialog.getBuilder().title(title).build();
            dialog.show();
        } else {
            MaterialDialog.Builder builder = MaterialDialogUtils.showIndeterminateProgressDialog(getActivity(), title, true);
            dialog = builder.show();
        }
    }

    public void dismissLoading() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /**
     * 跳转页面
     *
     * @param clz 所跳转的目的Activity类
     */
    public void startActivity(Class<?> clz) {
        startActivity(new Intent(getContext(), clz));
    }

    /**
     * 跳转页面
     *
     * @param clz    所跳转的目的Activity类
     * @param bundle 跳转所携带的信息
     */
    public void startActivity(Class<?> clz, Bundle bundle) {
        Intent intent = new Intent(getContext(), clz);
        if (bundle != null) {
            intent.putExtras(bundle);
        }
        startActivity(intent);
    }

    @Override
    public void initParam() {

    }

    /**
     * 初始化根布局
     *
     * @return 布局layout的id
     */
    public abstract int initContentView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    /**
     * 初始化ViewModel的id
     *
     * @return BR的id
     */
    public abstract int initVariableId();

    /**
     * 初始化ViewModel
     *
     * @return 继承BaseViewModel的ViewModel
     */
    public VM initViewModel() {
        return null;
    }

    @Override
    public void initData() {

    }

    public void initToolbar() {
    }

    @Override
    public void initViewObservable() {

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.unbind();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
