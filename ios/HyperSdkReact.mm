/*
 * Copyright (c) Juspay Technologies.
 *
 * This source code is licensed under the AGPL 3.0 license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import "HyperSdkReact.h"

#import <Foundation/Foundation.h>

#import <React/RCTLog.h>
#import <React/RCTConvert.h>
#import <React/RCTUIManager.h>
#import <React/RCTUtils.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTModalHostViewController.h>
#import <React/RCTRootView.h>

#import <HyperSDK/HyperSDK.h>

// Static reference to the map of instances so that the view managers can fetch an instance by key.
static NSMutableDictionary<NSString *, HyperServices *> *_hyperServicesReferences;

// Normalises a merchant supplied key. A nil/empty key always maps to "default".
static NSString *normalizeHyperKey(NSString *key) {
    if (key == nil || key.length == 0) {
        return @"default";
    }
    return key;
}

// Overriding the RCTRootView to add contraints to align with the views superview
@implementation SDKRootView

-(void)didMoveToSuperview {
    // Remove old leading anchor
    if (self.leading.isActive) {
        self.leading.active = @NO;
    }
    // Remove old trailing anchor
    if (self.trailing.isActive) {
        self.trailing.active = @NO;
    }
    
    //Checking superview just to be sure that it is not nil
    if(self.superview) {
        // Create contraints to replicate wrapcontent
        self.leading = [self.leadingAnchor constraintEqualToAnchor:self.superview.leadingAnchor];
        self.trailing = [self.trailingAnchor constraintEqualToAnchor:self.superview.trailingAnchor];
        // Save contraints so that it can be removed if there is superview is changed.
        // This should not happen as per usecase
        self.leading.active = @YES;
        self.trailing.active = @YES;
    }
}

@end


@implementation SdkDelegate

NSMutableSet<NSString *> *registeredComponents = [[NSMutableSet alloc] init];

- (id)initWithBridge:(RCTBridge *)bridge {
    // Hold references to all merchant views provided to the sdk
    self.rootHolder = [[NSMutableDictionary alloc] init];
    // Hold latest vaule of height provided by react
    self.heightHolder = [[NSMutableDictionary alloc] init];
    // Hold reference to latest constraints so that they can be replaced if height is modified
    self.heightConstraintHolder = [[NSMutableDictionary alloc] init];
    // Hold reference to bridge so that RCTRootViews can share JS VM
    self.bridge = bridge;
    return self;
}

/**
 Create / replace height constraint given to set height of the view provided by the merchant
 */
- (void) setHeight: (NSNumber*)height forTag: (NSString * _Nonnull)tag {
    // Update the latest value of the height holder for the given tag
    // This will be used to set the height of view if view is created at a later point
    [self.heightHolder setObject: height forKey:tag];
    
    // Fetch previous height constraint so that it can be set to inactive
    NSLayoutConstraint *heightConstraint = [self.heightConstraintHolder objectForKey:tag];
    // Fetch rootview to update set constraints if view is already created
    UIView *rootView = [self.rootHolder objectForKey:tag];
    
    // Check if view is already present
    if (rootView && [rootView isKindOfClass: [UIView class]]) {
        // If present set earlier constraint to inactive
        if (heightConstraint && [heightConstraint isKindOfClass:[NSLayoutConstraint class]]) {
            heightConstraint.active = @NO;
        }
        // Set a new constraint with the latest height
        NSLayoutConstraint *newHeightConstraint = [rootView.heightAnchor constraintEqualToConstant: [height doubleValue]];
        newHeightConstraint.active = @YES;
        // Save the constraint so that it can be made inactive if a new constraint is created
        [self.heightConstraintHolder setObject:newHeightConstraint forKey:tag];
    }
}

/**
 Create a react root view
 Set height if available
 Use bridge to share the same JS VM
 */
- (UIView * _Nullable)merchantViewForViewType:(NSString * _Nonnull)viewType {
    
    // Create a SDKRootView so that we can attach width constraints once it is attached to it's parent
    RCTRootView *rrv = [SDKRootView alloc];
    NSString *moduleName = @"JP_003";
    if ([viewType isEqual:@"HEADER"] && [registeredComponents containsObject:@"JuspayHeader"]) {
        moduleName = @"JuspayHeader";
    } else if ([viewType isEqual:@"HEADER_ATTACHED"] && [registeredComponents containsObject:@"JuspayHeaderAttached"]) {
        moduleName = @"JuspayHeaderAttached";
    } else if ([viewType isEqual:@"FOOTER"] && [registeredComponents containsObject:@"JuspayFooter"]) {
        moduleName = @"JuspayFooter";
    } else if ([viewType isEqual:@"FOOTER_ATTACHED"] && [registeredComponents containsObject:@"JuspayFooterAttached"]) {
        moduleName = @"JuspayFooterAttached";
    }
    
    // Save a reference of the react root view
    // This will be used to update height constraint if a newer value is sent by the merchant
    [self.rootHolder setObject:rrv forKey:moduleName];
    
    
    rrv = [rrv initWithBridge: self.bridge
                   moduleName:moduleName
            initialProperties:nil
    ];
    
    // Remove background colour. Default colour white is getting applied to the merchant view
    rrv.backgroundColor = UIColor.clearColor ;
    
    // Remove height 0, width 0 constraints added by default.
    rrv.translatesAutoresizingMaskIntoConstraints = false;
    
    // If height is available set the height
    NSNumber *height = [self.heightHolder objectForKey:moduleName];
    if (height && [height isKindOfClass:[NSNumber class]]) {
        NSLayoutConstraint *heightConstriant = [rrv.heightAnchor constraintEqualToConstant: [height doubleValue]];
        heightConstriant.active = @YES;
        [self.heightConstraintHolder setObject:heightConstriant forKey:moduleName];
    }
    // This is sent to hypersdk. Hyper sdk adds the view to it's heirarchy and set's superview's top and bottom to match rrv's top and bottom
    return rrv;
}

- (void) onWebViewReady:(WKWebView *)webView {
    //Ignored
}

@end

@implementation HyperSdkReact
RCT_EXPORT_MODULE()

NSString *HYPER_EVENT = @"HyperEvent";
NSString *JUSPAY_HEADER = @"JuspayHeader";
NSString *JUSPAY_FOOTER = @"JuspayFooter";
NSString *JUSPAY_HEADER_ATTACHED = @"JuspayHeaderAttached";
NSString *JUSPAY_FOOTER_ATTACHED = @"JuspayFooterAttached";

- (dispatch_queue_t)methodQueue{
    return dispatch_get_main_queue();
}

+ (BOOL)requiresMainQueueSetup{
    return YES;
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"HyperEvent"];
}

- (NSDictionary *)constantsToExport
{
    return @{ HYPER_EVENT: HYPER_EVENT
              , JUSPAY_HEADER : JUSPAY_HEADER
              , JUSPAY_HEADER_ATTACHED : JUSPAY_HEADER_ATTACHED
              , JUSPAY_FOOTER : JUSPAY_FOOTER
              , JUSPAY_FOOTER_ATTACHED : JUSPAY_FOOTER_ATTACHED
    };
}

// Will be called when this module's first listener is added.
-(void)startObserving {
    // Set up any upstream listeners or background tasks as necessary
}

// Will be called when this module's last listener is removed, or on dealloc.
-(void)stopObserving {
    // Remove upstream listeners, stop unnecessary background tasks
}

// Lazily creates the map holding every HyperServices instance.
- (NSMutableDictionary<NSString *, HyperServices *> *)hyperInstances {
    if (_hyperInstances == nil) {
        _hyperInstances = [[NSMutableDictionary alloc] init];
        _hyperServicesReferences = _hyperInstances;
    }
    return _hyperInstances;
}

// Lazily creates the map holding the delegate of every instance.
- (NSMutableDictionary<NSString *, id<HyperDelegate>> *)delegates {
    if (_delegates == nil) {
        _delegates = [[NSMutableDictionary alloc] init];
    }
    return _delegates;
}

// Returns the instance registered for the (normalised) key, or nil.
- (HyperServices *)instanceForKey:(NSString *)key {
    return [self.hyperInstances objectForKey:normalizeHyperKey(key)];
}

// Wraps the SDK event as { key, data } (matching Android) and emits it to JS.
- (void)sendHyperEventForKey:(NSString *)key data:(NSDictionary<NSString *, id> *)data {
    NSDictionary *wrapped = @{ @"key": normalizeHyperKey(key), @"data": data ?: @{} };
    [self sendEventWithName:@"HyperEvent" body:[[self class] dictionaryToString:wrapped]];
}

RCT_EXPORT_METHOD(preFetch:(NSString *)data) {
    if (data && data.length>0) {
        @try {
            NSDictionary *jsonData = [HyperSdkReact stringToDictionary:data];
            if (jsonData && [jsonData isKindOfClass:[NSDictionary class]] && jsonData.allKeys.count>0) {
                [HyperServices preFetch:jsonData];
            } else {
                
            }
        } @catch (NSException *exception) {
            //Parsing failure.
        }
    }
}

RCT_EXPORT_METHOD(createHyperServices:(NSString *)key) {
    key = normalizeHyperKey(key);
    if ([self.hyperInstances objectForKey:key] == nil) {
        HyperServices *instance = [HyperServices new];
        [self.hyperInstances setObject:instance forKey:key];
        _hyperServicesReferences = self.hyperInstances;
    }
}

RCT_EXPORT_METHOD(initiate:(NSString *)data key:(NSString *)key) {
    key = normalizeHyperKey(key);
    if (data && data.length>0) {
        @try {
            NSDictionary *jsonData = [HyperSdkReact stringToDictionary:data];
            if (jsonData && [jsonData isKindOfClass:[NSDictionary class]] && jsonData.allKeys.count>0) {

                HyperServices *instance = [self instanceForKey:key];
                if (instance == nil) {
                    return;
                }
                UIViewController *baseViewController = RCTPresentedViewController();
                __weak HyperSdkReact *weakSelf = self;
                // Keep a delegate alive per key so each instance keeps its own merchant views.
                id<HyperDelegate> delegate = [[SdkDelegate alloc] initWithBridge:self.bridge];
                [self.delegates setObject:delegate forKey:key];
                [instance setHyperDelegate:delegate];
                [instance initiate:baseViewController payload:jsonData callback:^(NSDictionary<NSString *,id> * _Nullable data) {
                    [weakSelf sendHyperEventForKey:key data:data];
                }];
            } else {
                // Define proper error code and return proper error
                // [self sendEventWithName:@"HyperEvent" body:[[self class] dictionaryToString:data]];
            }
        } @catch (NSException *exception) {
            // Define proper error code and return proper error
            // [self sendEventWithName:@"HyperEvent" body:[[self class] dictionaryToString:data]];
        }
    } else {
        // Define proper error code and return proper error
        // [self sendEventWithName:@"HyperEvent" body:[[self class] dictionaryToString:data]];
    }
}

RCT_EXPORT_METHOD(process:(NSString *)data key:(NSString *)key) {
    [self processData:data key:key];
}

// iOS has no separate activity concept like Android, so processWithActivity maps to process.
RCT_EXPORT_METHOD(processWithActivity:(NSString *)data key:(NSString *)key) {
    [self processData:data key:key];
}

// iOS has no dedicated payment page activity, so openPaymentPage maps to process.
RCT_EXPORT_METHOD(openPaymentPage:(NSString *)data key:(NSString *)key) {
    [self processData:data key:key];
}

// Shared implementation that forwards a process payload to the instance for the given key.
- (void)processData:(NSString *)data key:(NSString *)key {
    HyperServices *instance = [self instanceForKey:key];
    if (instance == nil) {
        return;
    }
    if (data && data.length>0) {
        @try {
            NSDictionary *jsonData = [HyperSdkReact stringToDictionary:data];
            // Update baseViewController if it's nil or not in the view hierarchy.
            if (instance.baseViewController == nil || instance.baseViewController.view.window == nil) {
                // Getting topViewController
                id baseViewController = RCTPresentedViewController();

                // Set the presenting ViewController as baseViewController if the topViewController is RCTModalHostViewController.
                if ([baseViewController isMemberOfClass:RCTModalHostViewController.class] && [baseViewController presentingViewController]) {
                    [instance setBaseViewController:[baseViewController presentingViewController]];
                } else {
                    [instance setBaseViewController:baseViewController];
                }
            }
            if (jsonData && [jsonData isKindOfClass:[NSDictionary class]] && jsonData.allKeys.count>0) {
                [instance process:jsonData];
            } else {
                // Define proper error code and return proper error
                // [self sendEventWithName:@"HyperEvent" body:[[self class] dictionaryToString:data]];
            }
        } @catch (NSException *exception) {
            // Define proper error code and return proper error
            // [self sendEventWithName:@"HyperEvent" body:[[self class] dictionaryToString:data]];
        }
    } else {
        // Define proper error code and return proper error
        // [self sendEventWithName:@"HyperEvent" body:[[self class] dictionaryToString:data]];
    }
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isNull:(NSString *)key) {
    return [self instanceForKey:key] == nil ? @true : @false;
}

// iOS has no hardware back button concept, so always report unhandled.
RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(onBackPressed:(NSString *)key) {
    return @false;
}

RCT_EXPORT_METHOD(terminate:(NSString *)key) {
    key = normalizeHyperKey(key);
    HyperServices *instance = [self instanceForKey:key];
    if (instance) {
        [instance terminate];
    }
    [self.hyperInstances removeObjectForKey:key];
    [self.delegates removeObjectForKey:key];
}

RCT_EXPORT_METHOD(terminateAll) {
    for (NSString *key in [self.hyperInstances.allKeys copy]) {
        @try {
            [self terminate:key];
        } @catch (NSException *exception) {}
    }
}

RCT_EXPORT_METHOD(notifyAboutRegisterComponent:(NSString *)viewType) {
    [registeredComponents addObject:viewType];
}

RCT_EXPORT_METHOD(isInitialised:(NSString *)key resolve:(RCTPromiseResolveBlock)resolve  reject:(RCTPromiseRejectBlock)reject) {
    HyperServices *instance = [self instanceForKey:key];
    if (instance) {
        resolve(instance.isInitialised? @true : @false);
    } else {
        resolve(@false);
    }
}

RCT_EXPORT_METHOD(updateBaseViewController) {
    // No key is passed for this call, so refresh every initialised instance.
    UIViewController *presented = RCTPresentedViewController();
    for (HyperServices *instance in self.hyperInstances.allValues) {
        if (instance && [instance isInitialised]) {
            instance.baseViewController = presented;
        }
    }
}

RCT_EXPORT_METHOD(updateMerchantViewHeight: (NSString * _Nonnull) tag height: (NSNumber * _Nonnull) h) {
    // Merchant view module names are shared across instances, so update every delegate.
    for (id<HyperDelegate> delegate in self.delegates.allValues) {
        if ([delegate isKindOfClass:[SdkDelegate class]]) {
            [((SdkDelegate *) delegate) setHeight:h forTag:tag];
        }
    }
}

+ (NSDictionary*)stringToDictionary:(NSString*)string{
    if (string.length<1) {
        return @{};
    }
    NSError *error;
    NSData *data = [string dataUsingEncoding:NSUTF8StringEncoding];
    id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (error) {}
    return json;
}

+ (NSString*)dictionaryToString:(id)dict{
    if (!dict || ![NSJSONSerialization isValidJSONObject:dict]) {
        return @"";
    }
    NSString *data = [[NSString alloc] initWithData:[NSJSONSerialization dataWithJSONObject:dict options:0 error:nil] encoding:NSUTF8StringEncoding];
    return data;
}

+ (HyperServices *)getHyperInstance {
  return [self getHyperInstanceForKey:@"default"];
}

+ (HyperServices *)getHyperInstanceForKey:(NSString *)key {
  return [_hyperServicesReferences objectForKey:normalizeHyperKey(key)];
}

@end

@implementation HyperFragmentViewManagerIOS
RCT_EXPORT_MODULE()

- (dispatch_queue_t)methodQueue{
    return dispatch_get_main_queue();
}

+ (BOOL)requiresMainQueueSetup{
    return YES;
}

- (UIView *)view
{
    return [[UIView alloc] init];
}

RCT_EXPORT_METHOD(process:(nonnull NSNumber *)viewTag nameSpace:(NSString *)nameSpace payload:(NSString *)payload key:(NSString *)key)
{
    HyperServices *hyperServicesInstance = [HyperSdkReact getHyperInstanceForKey:key];
    if (payload && payload.length>0) {
        @try {
            NSDictionary *jsonData = [HyperSdkReact stringToDictionary:payload];
            if (jsonData && [jsonData isKindOfClass:[NSDictionary class]] && jsonData.allKeys.count>0) {
                [self.bridge.uiManager addUIBlock:^(RCTUIManager *uiManager, NSDictionary<NSNumber *, UIView *> *viewRegistry) {
                    if (hyperServicesInstance.baseViewController == nil || hyperServicesInstance.baseViewController.view.window == nil) {
                        id baseViewController = RCTPresentedViewController();
                        if ([baseViewController isMemberOfClass:RCTModalHostViewController.class] && [baseViewController presentingViewController]) {
                            [hyperServicesInstance setBaseViewController:[baseViewController presentingViewController]];
                        } else {
                            [hyperServicesInstance setBaseViewController:baseViewController];
                        }
                    }
                    UIView *view = viewRegistry[viewTag];
                    [self manuallyLayoutChildren:view];
                    if (!view || ![view isKindOfClass:[UIView class]]) {
                        RCTLogError(@"Cannot find NativeViewManager with tag #%@", viewTag);
                        return;
                    }
                    NSMutableDictionary *nestedPayload = [jsonData[@"payload"] mutableCopy];
                    NSDictionary *fragmentViewGroup = @{nameSpace: view};
                    nestedPayload[@"fragmentViewGroups"] = fragmentViewGroup;
                    NSMutableDictionary *updatedJsonData = [jsonData mutableCopy];
                    updatedJsonData[@"payload"] = nestedPayload;
                    [hyperServicesInstance process:[updatedJsonData copy]];
                }];
            } else {}
        } @catch (NSException *exception) {}
    } else {}
}

- (void)manuallyLayoutChildren:(UIView *)view {
    UIView *parent = view.superview;
    if (!parent) return;
    
    view.frame = parent.bounds;
}

@end
